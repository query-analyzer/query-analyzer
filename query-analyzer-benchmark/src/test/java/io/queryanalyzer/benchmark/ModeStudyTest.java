package io.queryanalyzer.benchmark;

import io.queryanalyzer.core.analyzer.SqlNormalizer;
import io.queryanalyzer.core.config.DetectorConfig;
import io.queryanalyzer.core.detector.NPlusOneDetector;
import io.queryanalyzer.core.model.QueryInfo;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Controlled experiment that isolates the contribution of the confidence model,
 * answering the question a reviewer will ask: "the accuracy benchmark shows THRESHOLD
 * and CONFIDENCE both at F1=1.00 - so what does the confidence machinery add?"
 *
 * <p>It uses synthetic micro-traces with fully controlled signals (crafted stack frames
 * and timestamps) so each of the three confidence signals - framework stack, timing,
 * code-location pattern - can be exercised in isolation. Verdicts are produced by the
 * real detector; nothing is hand-scored. Three analyses are reported:</p>
 *
 * <ol>
 *   <li><b>Mode comparison</b> - THRESHOLD vs CONFIDENCE vs HYBRID on a corpus that
 *       includes "scattered repeats" (the same query issued from several different call
 *       sites in one request), a legitimate pattern that a pure count threshold
 *       misclassifies as N+1.</li>
 *   <li><b>Ablation</b> - each confidence signal is zeroed (weights renormalised) to
 *       measure its effect on precision/recall.</li>
 *   <li><b>Threshold sensitivity</b> - the decision threshold is swept to justify the
 *       default of 0.5.</li>
 * </ol>
 *
 * <p>This is a controlled study, complementing (not replacing) the real-JPA accuracy
 * benchmark and the spring-petclinic case study.</p>
 */
class ModeStudyTest {

    private record Case(String name, boolean isNPlusOne, List<QueryInfo> trace) {
    }

    private static final Instant BASE = Instant.ofEpochMilli(1_700_000_000_000L);

    @Test
    void modeComparisonAblationAndSensitivity() throws IOException {
        List<Case> corpus = buildCorpus();

        StringBuilder md = new StringBuilder();
        md.append("# Controlled study: confidence model contribution\n\n");
        md.append(corpus.size()).append(" synthetic micro-traces with controlled signals. ")
                .append("Verdicts produced by the real detector.\n\n");

        // ---- 1. Mode comparison -------------------------------------------------
        md.append("## 1. Mode comparison (THRESHOLD vs CONFIDENCE vs HYBRID)\n\n");
        md.append("| Mode | TP | FP | TN | FN | Precision | Recall | F1 |\n");
        md.append("|---|---|---|---|---|---|---|---|\n");
        System.out.println("\n============== CONTROLLED MODE STUDY ==============");
        for (DetectorConfig.DetectionMode mode : DetectorConfig.DetectionMode.values()) {
            DetectorConfig cfg = DetectorConfig.builder().detectionMode(mode).minRepetitions(3).build();
            md.append(metricsRow(mode.name(), corpus, cfg));
        }

        // ---- 2. Ablation of confidence signals ---------------------------------
        md.append("\n## 2. Ablation (CONFIDENCE mode, one signal zeroed at a time)\n\n");
        md.append("Weights are renormalised to sum to 1.0 after zeroing a signal.\n\n");
        md.append("| Configuration | stack | timing | pattern | Precision | Recall | F1 |\n");
        md.append("|---|---|---|---|---|---|---|\n");
        md.append(ablationRow("full model", corpus, 0.5, 0.2, 0.3));
        md.append(ablationRow("no stack signal", corpus, 0.0, 0.4, 0.6));
        md.append(ablationRow("no timing signal", corpus, 0.625, 0.0, 0.375));
        md.append(ablationRow("no pattern signal", corpus, 0.714, 0.286, 0.0));

        // ---- 3. Threshold sensitivity ------------------------------------------
        md.append("\n## 3. Threshold sensitivity (CONFIDENCE mode)\n\n");
        md.append("| minConfidence | Precision | Recall | F1 |\n");
        md.append("|---|---|---|---|\n");
        for (double t = 0.1; t <= 0.901; t += 0.1) {
            DetectorConfig cfg = DetectorConfig.builder()
                    .detectionMode(DetectorConfig.DetectionMode.CONFIDENCE)
                    .minRepetitions(3)
                    .minConfidenceThreshold(round(t))
                    .build();
            Metrics m = evaluate(corpus, cfg);
            md.append(String.format("| %.1f | %.2f | %.2f | %.2f |%n",
                    round(t), m.precision(), m.recall(), m.f1()));
        }

        System.out.println("Report written to target/benchmark-mode-study.md");
        System.out.println("==================================================\n");

        Path outDir = Path.of("target");
        Files.createDirectories(outDir);
        Files.writeString(outDir.resolve("benchmark-mode-study.md"), md.toString());

        // Sanity: the experiment must actually discriminate the modes, otherwise it is
        // not informative. CONFIDENCE should be at least as precise as THRESHOLD here.
        Metrics threshold = evaluate(corpus,
                DetectorConfig.builder().detectionMode(DetectorConfig.DetectionMode.THRESHOLD)
                        .minRepetitions(3).build());
        Metrics confidence = evaluate(corpus,
                DetectorConfig.builder().detectionMode(DetectorConfig.DetectionMode.CONFIDENCE)
                        .minRepetitions(3).build());
        assertTrue(confidence.precision() >= threshold.precision(),
                "Confidence precision should be >= threshold precision on this corpus");
    }

    // ---------------------------------------------------------------- corpus

    private List<Case> buildCorpus() {
        List<Case> c = new ArrayList<>();

        // True N+1, strong ORM lazy-loading stack, tight loop, one call site.
        c.add(new Case("n1_orm_lazyload", true,
                repeat("select * from orders where user_id = ?", 5, ormStack(), 3)));

        // True N+1 with a WEAK stack (plain JDBC driver, no ORM frames). Only timing +
        // location can catch it - exercises whether confidence still detects it.
        c.add(new Case("n1_weak_stack_jdbc", true,
                repeat("select * from line_item where order_id = ?", 5, jdbcStack(), 3)));

        // True N+1 at exactly the repetition threshold.
        c.add(new Case("n1_at_threshold", true,
                repeat("select * from address where person_id = ?", 3, ormStack(), 3)));

        // Legitimate: the SAME lookup issued from several DIFFERENT call sites in one
        // request (e.g. shared reference data read by multiple services). A pure count
        // threshold misclassifies this as N+1; confidence should not.
        c.add(new Case("scattered_repeats_4_sites", false,
                scattered("select * from country where code = ?", 4)));

        c.add(new Case("scattered_repeats_3_sites", false,
                scattered("select * from currency where code = ?", 3)));

        // Below the repetition threshold - not enough evidence for N+1.
        c.add(new Case("below_threshold_2x", false,
                repeat("select * from settings where key = ?", 2, ormStack(), 3)));

        return c;
    }

    /** Same query, same call site, tight timing. */
    private List<QueryInfo> repeat(String sql, int n, StackTraceElement[] stack, long gapMs) {
        List<QueryInfo> trace = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            trace.add(q(sql, stack, i * gapMs));
        }
        return trace;
    }

    /** Same query, tight timing, but each execution from a DIFFERENT call site. */
    private List<QueryInfo> scattered(String sql, int n) {
        List<QueryInfo> trace = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            StackTraceElement[] stack = {
                    new StackTraceElement("com.example.app.Service" + i, "method" + i, "Service.java", 10 + i)
            };
            trace.add(q(sql, stack, i * 3L));
        }
        return trace;
    }

    private QueryInfo q(String sql, StackTraceElement[] stack, long offsetMs) {
        return new QueryInfo(sql, SqlNormalizer.normalize(sql), 0L,
                BASE.plusMillis(offsetMs), stack, "t", null);
    }

    private StackTraceElement[] ormStack() {
        return new StackTraceElement[]{
                new StackTraceElement("org.hibernate.collection.internal.PersistentBag", "read", "PersistentBag.java", 344),
                new StackTraceElement("org.hibernate.loader.entity.EntityLoader", "load", "EntityLoader.java", 100),
                new StackTraceElement("com.example.app.OrderService", "loadOrders", "OrderService.java", 42)
        };
    }

    private StackTraceElement[] jdbcStack() {
        return new StackTraceElement[]{
                new StackTraceElement("org.postgresql.jdbc.PgPreparedStatement", "executeQuery", "PgPreparedStatement.java", 120),
                new StackTraceElement("com.example.app.ReportService", "run", "ReportService.java", 10)
        };
    }

    // ---------------------------------------------------------------- evaluation

    private record Metrics(int tp, int fp, int tn, int fn) {
        double precision() {
            return tp + fp == 0 ? 0.0 : (double) tp / (tp + fp);
        }

        double recall() {
            return tp + fn == 0 ? 0.0 : (double) tp / (tp + fn);
        }

        double f1() {
            double p = precision();
            double r = recall();
            return p + r == 0 ? 0.0 : 2 * p * r / (p + r);
        }
    }

    private Metrics evaluate(List<Case> corpus, DetectorConfig cfg) {
        int tp = 0, fp = 0, tn = 0, fn = 0;
        for (Case c : corpus) {
            boolean flagged = !new NPlusOneDetector(cfg).detect(c.trace()).isEmpty();
            if (c.isNPlusOne() && flagged) tp++;
            else if (!c.isNPlusOne() && flagged) fp++;
            else if (!c.isNPlusOne()) tn++;
            else fn++;
        }
        return new Metrics(tp, fp, tn, fn);
    }

    private String metricsRow(String label, List<Case> corpus, DetectorConfig cfg) {
        Metrics m = evaluate(corpus, cfg);
        System.out.printf("%-12s TP=%d FP=%d TN=%d FN=%d | P=%.2f R=%.2f F1=%.2f%n",
                label, m.tp(), m.fp(), m.tn(), m.fn(), m.precision(), m.recall(), m.f1());
        return String.format("| %s | %d | %d | %d | %d | %.2f | %.2f | %.2f |%n",
                label, m.tp(), m.fp(), m.tn(), m.fn(), m.precision(), m.recall(), m.f1());
    }

    private String ablationRow(String label, List<Case> corpus, double stack, double timing, double pattern) {
        DetectorConfig cfg = DetectorConfig.builder()
                .detectionMode(DetectorConfig.DetectionMode.CONFIDENCE)
                .minRepetitions(3)
                .stackTraceWeight(stack).timingWeight(timing).patternWeight(pattern)
                .build();
        Metrics m = evaluate(corpus, cfg);
        return String.format("| %s | %.3f | %.3f | %.3f | %.2f | %.2f | %.2f |%n",
                label, stack, timing, pattern, m.precision(), m.recall(), m.f1());
    }

    private static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
