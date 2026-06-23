package io.queryanalyzer.benchmark;

import io.queryanalyzer.benchmark.baseline.NaiveRepeatedSelectDetector;
import io.queryanalyzer.benchmark.capture.CapturedQueries;
import io.queryanalyzer.benchmark.model.Order;
import io.queryanalyzer.benchmark.model.User;
import io.queryanalyzer.benchmark.repository.OrderRepository;
import io.queryanalyzer.benchmark.repository.UserRepository;
import io.queryanalyzer.core.config.DetectorConfig;
import io.queryanalyzer.core.detector.NPlusOneDetector;
import io.queryanalyzer.core.model.QueryInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Accuracy benchmark: runs a labelled suite of realistic JPA workloads, captures each
 * one's SQL trace, and replays the identical trace to every detector under comparison.
 * Emits a per-scenario CSV and a precision/recall/F1 summary (console + markdown).
 *
 * <p>Detectors compared:</p>
 * <ul>
 *   <li>query-analyzer THRESHOLD (count-based, minRepetitions=3)</li>
 *   <li>query-analyzer CONFIDENCE (multi-signal scoring)</li>
 *   <li>query-analyzer HYBRID (both must agree)</li>
 *   <li>NaiveRepeatedSelect (models QuickPerf-style count heuristics; baseline)</li>
 * </ul>
 *
 * <p>This test is report-only: it does not assert specific verdicts (it is a measurement,
 * not a contract). The single sanity assertion is that capture actually produced a trace.</p>
 */
@DataJpaTest
class AccuracyBenchmarkTest {

    @Autowired
    private TestEntityManager em;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private OrderRepository orderRepository;

    private static final int USERS = 5;
    private static final int ORDERS_PER_USER = 3;

    private record Scenario(String name, boolean isNPlusOne, Runnable work) {
    }

    private record Row(String scenario, boolean truth, Map<String, Boolean> verdicts, int traceSize) {
    }

    @Test
    void runAccuracyBenchmark() throws IOException {
        seed();

        // Detectors under test. Each maps a captured trace -> "flagged as N+1?".
        Map<String, Predicate<List<QueryInfo>>> detectors = new LinkedHashMap<>();
        DetectorConfig thresholdCfg = DetectorConfig.builder()
                .detectionMode(DetectorConfig.DetectionMode.THRESHOLD).minRepetitions(3).build();
        DetectorConfig confidenceCfg = DetectorConfig.builder()
                .detectionMode(DetectorConfig.DetectionMode.CONFIDENCE).minRepetitions(3).build();
        DetectorConfig hybridCfg = DetectorConfig.builder()
                .detectionMode(DetectorConfig.DetectionMode.HYBRID).minRepetitions(3).build();
        detectors.put("QA-THRESHOLD", t -> !new NPlusOneDetector(thresholdCfg).detect(t).isEmpty());
        detectors.put("QA-CONFIDENCE", t -> !new NPlusOneDetector(confidenceCfg).detect(t).isEmpty());
        detectors.put("QA-HYBRID", t -> !new NPlusOneDetector(hybridCfg).detect(t).isEmpty());
        detectors.put("Naive(QuickPerf-style)", t -> new NaiveRepeatedSelectDetector(2).detects(t));

        List<Scenario> scenarios = List.of(
                new Scenario("classic_n_plus_one (lazy collection)", true, this::workClassicNPlusOne),
                new Scenario("n_plus_one_via_query_method", true, this::workNPlusOneQueryMethod),
                new Scenario("join_fetch (optimized)", false, this::workJoinFetch),
                new Scenario("pagination_loop", false, this::workPaginationLoop),
                new Scenario("streaming_poll (timestamp)", false, this::workStreamingPoll),
                new Scenario("batched_in_clause", false, this::workBatchedIn),
                new Scenario("repeated_below_threshold", false, this::workRepeatedBelowThreshold)
        );

        List<Row> rows = new ArrayList<>();
        int totalCaptured = 0;
        for (Scenario s : scenarios) {
            em.clear(); // empty the persistence context so lazy loads actually re-fire
            CapturedQueries.start();
            s.work().run();
            List<QueryInfo> trace = CapturedQueries.stop();
            totalCaptured += trace.size();

            Map<String, Boolean> verdicts = new LinkedHashMap<>();
            for (var e : detectors.entrySet()) {
                boolean flagged;
                try {
                    flagged = e.getValue().test(trace);
                } catch (RuntimeException ex) {
                    flagged = false;
                }
                verdicts.put(e.getKey(), flagged);
            }
            rows.add(new Row(s.name(), s.isNPlusOne(), verdicts, trace.size()));
        }

        assertFalse(totalCaptured == 0,
                "No queries were captured - the StatementInspector is not wired. "
                        + "Check spring.jpa.properties.hibernate.session_factory.statement_inspector.");

        report(rows, new ArrayList<>(detectors.keySet()));
    }

    // ---------------------------------------------------------------- workloads

    /** Classic N+1: load users, then trigger lazy collection load per user. */
    private void workClassicNPlusOne() {
        List<User> users = userRepository.findAll();
        long sink = 0;
        for (User u : users) {
            sink += u.getOrders().size();
        }
        blackhole(sink);
    }

    /** N+1 expressed as one repository SELECT per parent row. */
    private void workNPlusOneQueryMethod() {
        List<User> users = userRepository.findAll();
        long sink = 0;
        for (User u : users) {
            sink += orderRepository.findByUserId(u.getId()).size();
        }
        blackhole(sink);
    }

    /** Optimized: a single JOIN FETCH; accessing the collection issues no extra query. */
    private void workJoinFetch() {
        List<User> users = userRepository.findAllWithOrders();
        long sink = 0;
        for (User u : users) {
            sink += u.getOrders().size();
        }
        blackhole(sink);
    }

    /** Legitimate pagination: same SELECT shape, increasing offset window. */
    private void workPaginationLoop() {
        long sink = 0;
        for (int page = 0; page < 5; page++) {
            sink += orderRepository.findByProductNameNotNull(PageRequest.of(page, 2)).size();
        }
        blackhole(sink);
    }

    /** Streaming/polling: repeated timestamp-filtered read at a steady cadence. */
    private void workStreamingPoll() {
        LocalDateTime threshold = LocalDateTime.now().minusYears(10);
        long sink = 0;
        for (int i = 0; i < 5; i++) {
            sink += orderRepository.findByOrderDateAfter(threshold).size();
            sleepQuietly(10);
        }
        blackhole(sink);
    }

    /** Batched fetch: one statement using IN (...). */
    private void workBatchedIn() {
        List<Long> ids = userRepository.findAll().stream().map(User::getId).toList();
        em.clear();
        CapturedQueries.start(); // restart so the findAll above is not counted
        blackhole(orderRepository.findByUserIdIn(ids).size());
    }

    /** Two lookups of the same shape - repetition below any N+1 threshold. */
    private void workRepeatedBelowThreshold() {
        long sink = lookupFirstUserOrders() + lookupFirstUserOrdersAgain();
        blackhole(sink);
    }

    private long lookupFirstUserOrders() {
        return orderRepository.findByUserId(1L).size();
    }

    private long lookupFirstUserOrdersAgain() {
        return orderRepository.findByUserId(1L).size();
    }

    // ---------------------------------------------------------------- reporting

    private void report(List<Row> rows, List<String> detectorNames) throws IOException {
        Path outDir = Path.of("target");
        Files.createDirectories(outDir);

        // Per-scenario CSV
        StringBuilder csv = new StringBuilder("scenario,ground_truth_n_plus_one,trace_size");
        for (String d : detectorNames) {
            csv.append(',').append(d.replace(',', ';'));
        }
        csv.append('\n');
        for (Row r : rows) {
            csv.append('"').append(r.scenario()).append('"')
                    .append(',').append(r.truth())
                    .append(',').append(r.traceSize());
            for (String d : detectorNames) {
                csv.append(',').append(r.verdicts().get(d));
            }
            csv.append('\n');
        }
        Files.writeString(outDir.resolve("benchmark-accuracy.csv"), csv.toString());

        // Confusion matrix + metrics per detector
        StringBuilder md = new StringBuilder();
        md.append("# query-analyzer accuracy benchmark\n\n");
        md.append("Identical captured trace replayed to every detector. ")
                .append(rows.size()).append(" scenarios.\n\n");

        md.append("## Per-scenario verdicts (TRUE = flagged as N+1)\n\n");
        md.append("| Scenario | Ground truth | trace |");
        for (String d : detectorNames) {
            md.append(' ').append(d).append(" |");
        }
        md.append("\n|---|---|---|");
        for (int i = 0; i < detectorNames.size(); i++) {
            md.append("---|");
        }
        md.append('\n');
        for (Row r : rows) {
            md.append("| ").append(r.scenario())
                    .append(" | ").append(r.truth() ? "N+1" : "clean")
                    .append(" | ").append(r.traceSize()).append(" |");
            for (String d : detectorNames) {
                boolean v = r.verdicts().get(d);
                boolean correct = v == r.truth();
                md.append(' ').append(v ? "FLAG" : "pass")
                        .append(correct ? " OK" : " WRONG").append(" |");
            }
            md.append('\n');
        }

        md.append("\n## Metrics\n\n");
        md.append("| Detector | TP | FP | TN | FN | Precision | Recall | F1 | Accuracy |\n");
        md.append("|---|---|---|---|---|---|---|---|---|\n");

        System.out.println("\n================ ACCURACY BENCHMARK ================");
        for (String d : detectorNames) {
            int tp = 0, fp = 0, tn = 0, fn = 0;
            for (Row r : rows) {
                boolean v = r.verdicts().get(d);
                if (r.truth() && v) tp++;
                else if (!r.truth() && v) fp++;
                else if (!r.truth()) tn++;
                else fn++;
            }
            double precision = tp + fp == 0 ? 0.0 : (double) tp / (tp + fp);
            double recall = tp + fn == 0 ? 0.0 : (double) tp / (tp + fn);
            double f1 = precision + recall == 0 ? 0.0 : 2 * precision * recall / (precision + recall);
            double accuracy = (double) (tp + tn) / rows.size();
            md.append(String.format("| %s | %d | %d | %d | %d | %.2f | %.2f | %.2f | %.2f |%n",
                    d, tp, fp, tn, fn, precision, recall, f1, accuracy));
            System.out.printf("%-24s TP=%d FP=%d TN=%d FN=%d | P=%.2f R=%.2f F1=%.2f%n",
                    d, tp, fp, tn, fn, precision, recall, f1);
        }
        System.out.println("Reports written to target/benchmark-accuracy.csv and target/benchmark-report.md");
        System.out.println("===================================================\n");

        Files.writeString(outDir.resolve("benchmark-report.md"), md.toString());
    }

    // ---------------------------------------------------------------- fixtures

    private void seed() {
        LocalDateTime base = LocalDateTime.now().minusDays(30);
        for (int u = 0; u < USERS; u++) {
            User user = new User("User " + u, "user" + u + "@example.com");
            for (int o = 0; o < ORDERS_PER_USER; o++) {
                user.addOrder(new Order("Product-" + u + "-" + o,
                        new BigDecimal("19.99"), base.plusDays(o)));
            }
            em.persist(user);
        }
        em.flush();
        em.clear();
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void blackhole(long value) {
        if (value == Long.MIN_VALUE) {
            throw new IllegalStateException("unreachable");
        }
    }
}
