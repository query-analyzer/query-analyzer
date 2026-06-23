package io.queryanalyzer.benchmark;

import com.adgadev.jplusone.core.registry.OperationNodeView;
import com.adgadev.jplusone.core.registry.OperationType;
import com.adgadev.jplusone.core.registry.RootNode;
import com.adgadev.jplusone.core.registry.SessionNodeView;
import com.adgadev.jplusone.core.tracking.TrackingContext;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

/**
 * LIVE comparison against JPlusOne (adgadev) {@code jplusone-core:2.0.0} - a real,
 * automatic N+1 detector (not a manual statement counter like Hypersistence Utils).
 * It is the only other test-time N+1 tool ported to the Spring Boot 3 / Hibernate 6 /
 * Jakarta stack, so it runs live on the same setup as query-analyzer.
 *
 * <p>JPlusOne records a per-session operation tree. Its N+1 signal is repeated
 * <b>IMPLICIT</b> operations - i.e. lazy initialisations - issuing statements within a
 * session. We read that tree directly (no hand-scoring) and derive a per-scenario verdict
 * the same way JPlusOne's own report flags N+1: a lazy-loaded statement repeated within a
 * session. The identical workload is simultaneously captured for query-analyzer via the
 * Hibernate statement inspector, so both tools see the same execution.</p>
 *
 * <p>Report-only. Run with
 * {@code mvn -pl query-analyzer-benchmark test -Dtest=JPlusOneComparisonTest}.</p>
 */
@SpringBootTest(classes = BenchmarkApplication.class)
@TestPropertySource(properties = {
        "jplusone.enabled=true",
        "jplusone.application-root-package=io.queryanalyzer.benchmark",
        "jplusone.verbosity-level=VMAX"
})
class JPlusOneComparisonTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private TransactionTemplate tx;
    @Autowired
    private TrackingContext jPlusOneTracking;
    @Autowired
    private RootNode jPlusOneRoot;

    private record Scenario(String name, boolean isNPlusOne, Runnable work) {
    }

    @Test
    void compareAgainstJPlusOne() throws IOException {
        seedInTransaction();

        DetectorConfig autoCfg = DetectorConfig.builder()
                .detectionMode(DetectorConfig.DetectionMode.CONFIDENCE).minRepetitions(3).build();

        List<Scenario> scenarios = List.of(
                new Scenario("classic_n_plus_one (lazy collection)", true, this::workClassicNPlusOne),
                new Scenario("n_plus_one_via_query_method", true, this::workNPlusOneQueryMethod),
                new Scenario("join_fetch (optimized)", false, this::workJoinFetch),
                new Scenario("pagination_loop", false, this::workPaginationLoop),
                new Scenario("streaming_poll (timestamp)", false, this::workStreamingPoll),
                new Scenario("batched_in_clause", false, this::workBatchedIn),
                new Scenario("repeated_below_threshold", false, this::workRepeatedBelowThreshold)
        );

        jPlusOneTracking.enableRecording();

        StringBuilder md = new StringBuilder();
        md.append("# Live comparison: query-analyzer vs JPlusOne (jplusone-core 2.0.0)\n\n");
        md.append("Real automatic N+1 detector on the same Spring Boot 3 / Hibernate 6 stack. ")
                .append("JPlusOne verdict derived from its own recorded session tree ")
                .append("(repeated IMPLICIT/lazy-initialisation statements).\n\n");
        md.append("| Scenario | Ground truth | JPlusOne lazy-loads | JPlusOne verdict | query-analyzer |\n");
        md.append("|---|---|---|---|---|\n");

        int jpTp = 0, jpFp = 0, jpTn = 0, jpFn = 0;
        int qaTp = 0, qaFp = 0, qaTn = 0, qaFn = 0;

        System.out.println("\n========= LIVE: query-analyzer vs JPlusOne =========");
        System.out.printf("%-38s %-7s %-10s %-12s %s%n", "scenario", "truth", "JP-lazy", "JPlusOne", "QA");
        for (Scenario s : scenarios) {
            int sessBefore = jPlusOneRoot.getSessions().size();

            CapturedQueries.start();
            tx.executeWithoutResult(status -> s.work().run());
            List<QueryInfo> qaTrace = CapturedQueries.stop();

            List<? extends SessionNodeView> newSessions =
                    jPlusOneRoot.getSessions().subList(sessBefore, jPlusOneRoot.getSessions().size());
            int lazyOps = countLazyInitialisations(newSessions);
            int explicitOps = countOperations(newSessions, OperationType.EXPLICIT);
            System.out.printf("   [jp diag] sessions=%d implicitLazy=%d explicit=%d%n",
                    newSessions.size(), lazyOps, explicitOps);
            // JPlusOne reports N+1 when a session performs repeated lazy initialisations.
            boolean jpFlag = lazyOps >= 2;
            int repeatedLazy = lazyOps;
            boolean qaFlag = !new NPlusOneDetector(autoCfg).detect(qaTrace).isEmpty();

            if (s.isNPlusOne()) {
                if (jpFlag) jpTp++; else jpFn++;
                if (qaFlag) qaTp++; else qaFn++;
            } else {
                if (jpFlag) jpFp++; else jpTn++;
                if (qaFlag) qaFp++; else qaTn++;
            }

            md.append("| ").append(s.name())
                    .append(" | ").append(s.isNPlusOne() ? "N+1" : "clean")
                    .append(" | ").append(repeatedLazy)
                    .append(" | ").append(verdict(jpFlag, jpFlag == s.isNPlusOne()))
                    .append(" | ").append(verdict(qaFlag, qaFlag == s.isNPlusOne()))
                    .append(" |\n");
            System.out.printf("%-38s %-7s %-10d %-12s %s%n",
                    s.name(), s.isNPlusOne() ? "N+1" : "clean", repeatedLazy,
                    verdict(jpFlag, jpFlag == s.isNPlusOne()), verdict(qaFlag, qaFlag == s.isNPlusOne()));
        }

        md.append('\n');
        md.append(metricsLine("JPlusOne", jpTp, jpFp, jpTn, jpFn));
        md.append(metricsLine("query-analyzer", qaTp, qaFp, qaTn, qaFn));
        System.out.println(metricsLine("JPlusOne", jpTp, jpFp, jpTn, jpFn));
        System.out.println(metricsLine("query-analyzer", qaTp, qaFp, qaTn, qaFn));
        System.out.println("====================================================\n");

        Path outDir = Path.of("target");
        Files.createDirectories(outDir);
        Files.writeString(outDir.resolve("benchmark-jplusone.md"), md.toString());
    }

    /**
     * JPlusOne's N+1 signal: the number of IMPLICIT (lazy-initialisation) operations that
     * issued at least one statement within the scenario's sessions. Two or more repeated
     * lazy loads in a session is what JPlusOne's report flags as N+1.
     */
    private int countLazyInitialisations(List<? extends SessionNodeView> sessions) {
        // JPlusOne groups lazy loads from one call site into a single IMPLICIT operation,
        // so the N+1 magnitude is the number of STATEMENTS that operation issued.
        int count = 0;
        for (SessionNodeView session : sessions) {
            for (OperationNodeView op : session.getOperations()) {
                if (op.getOperationType() == OperationType.IMPLICIT) {
                    count += op.getStatements().size();
                }
            }
        }
        return count;
    }

    private int countOperations(List<? extends SessionNodeView> sessions, OperationType type) {
        int count = 0;
        for (SessionNodeView session : sessions) {
            for (OperationNodeView op : session.getOperations()) {
                if (op.getOperationType() == type) {
                    count++;
                }
            }
        }
        return count;
    }

    private static String verdict(boolean flagged, boolean correct) {
        return (flagged ? "FLAG" : "pass") + (correct ? " OK" : " WRONG");
    }

    private static String metricsLine(String name, int tp, int fp, int tn, int fn) {
        double p = tp + fp == 0 ? 0 : (double) tp / (tp + fp);
        double r = tp + fn == 0 ? 0 : (double) tp / (tp + fn);
        double f1 = p + r == 0 ? 0 : 2 * p * r / (p + r);
        return String.format("%-16s TP=%d FP=%d TN=%d FN=%d | P=%.2f R=%.2f F1=%.2f%n",
                name, tp, fp, tn, fn, p, r, f1);
    }

    // ---------------------------------------------------------------- workloads

    private void workClassicNPlusOne() {
        long sink = 0;
        for (User u : userRepository.findAll()) {
            sink += u.getOrders().size();
        }
        guard(sink);
    }

    private void workNPlusOneQueryMethod() {
        long sink = 0;
        for (User u : userRepository.findAll()) {
            sink += orderRepository.findByUserId(u.getId()).size();
        }
        guard(sink);
    }

    private void workJoinFetch() {
        long sink = 0;
        for (User u : userRepository.findAllWithOrders()) {
            sink += u.getOrders().size();
        }
        guard(sink);
    }

    private void workPaginationLoop() {
        long sink = 0;
        for (int page = 0; page < 5; page++) {
            sink += orderRepository.findByProductNameNotNull(PageRequest.of(page, 2)).size();
        }
        guard(sink);
    }

    private void workStreamingPoll() {
        LocalDateTime threshold = LocalDateTime.now().minusYears(10);
        long sink = 0;
        for (int i = 0; i < 5; i++) {
            sink += orderRepository.findByOrderDateAfter(threshold).size();
            sleepQuietly(10);
        }
        guard(sink);
    }

    private void workBatchedIn() {
        List<Long> ids = userRepository.findAll().stream().map(User::getId).toList();
        guard(orderRepository.findByUserIdIn(ids).size());
    }

    private void workRepeatedBelowThreshold() {
        guard(orderRepository.findByUserId(1L).size() + orderRepository.findByUserId(1L).size());
    }

    // ---------------------------------------------------------------- fixtures

    private void seedInTransaction() {
        tx.executeWithoutResult(status -> {
            LocalDateTime base = LocalDateTime.now().minusDays(30);
            for (int u = 0; u < 5; u++) {
                User user = new User("User " + u, "user" + u + "@example.com");
                for (int o = 0; o < 3; o++) {
                    user.addOrder(new Order("Product-" + u + "-" + o,
                            new BigDecimal("19.99"), base.plusDays(o)));
                }
                userRepository.save(user);
            }
        });
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void guard(long value) {
        if (value == Long.MIN_VALUE) {
            throw new IllegalStateException("unreachable");
        }
    }
}
