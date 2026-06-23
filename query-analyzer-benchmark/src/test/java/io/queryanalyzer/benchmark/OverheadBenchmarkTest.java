package io.queryanalyzer.benchmark;

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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Overhead benchmark: estimates the cost query-analyzer adds per request, separated into
 * (a) the capture hook and (b) the post-request analysis. Report-only.
 *
 * <p>This is a coarse wall-clock estimate (single JVM, warm H2), not a JMH micro-benchmark.
 * It exists to put a defensible order-of-magnitude on the "&lt;3ms / &lt;1%" README claim.</p>
 */
@DataJpaTest
class OverheadBenchmarkTest {

    @Autowired
    private TestEntityManager em;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private OrderRepository orderRepository;

    private static final int WARMUP = 200;
    private static final int ITERATIONS = 2000;

    @Test
    void measureOverhead() {
        seed();

        DetectorConfig cfg = DetectorConfig.builder()
                .detectionMode(DetectorConfig.DetectionMode.CONFIDENCE).minRepetitions(3).build();

        // Warmup (JIT) for both paths.
        for (int i = 0; i < WARMUP; i++) {
            runWorkloadNoCapture();
            List<QueryInfo> t = runWorkloadWithCapture();
            new NPlusOneDetector(cfg).detect(t);
        }

        long baselineNs = time(ITERATIONS, this::runWorkloadNoCapture);
        long captureNs = time(ITERATIONS, this::runWorkloadWithCapture);

        // Analysis cost measured separately on a representative captured trace.
        List<QueryInfo> trace = runWorkloadWithCapture();
        long analysisNs = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            long s = System.nanoTime();
            new NPlusOneDetector(cfg).detect(trace);
            analysisNs += System.nanoTime() - s;
        }

        double baseMs = baselineNs / 1_000_000.0 / ITERATIONS;
        double capMs = captureNs / 1_000_000.0 / ITERATIONS;
        double anaMs = analysisNs / 1_000_000.0 / ITERATIONS;
        double captureOverhead = capMs - baseMs;

        System.out.println("\n================ OVERHEAD BENCHMARK ================");
        System.out.printf("Workload (no capture)        : %.4f ms/req%n", baseMs);
        System.out.printf("Workload + capture hook      : %.4f ms/req%n", capMs);
        System.out.printf("  -> capture overhead        : %.4f ms/req%n", captureOverhead);
        System.out.printf("Post-request analysis        : %.4f ms/req (trace size %d)%n", anaMs, trace.size());
        System.out.printf("Total query-analyzer overhead: %.4f ms/req%n", captureOverhead + anaMs);
        System.out.println("Note: coarse wall-clock estimate on in-memory H2; absolute numbers");
        System.out.println("are machine-dependent. Use the deltas, not the absolutes.");
        System.out.println("===================================================\n");
    }

    private void runWorkloadNoCapture() {
        em.clear();
        List<User> users = userRepository.findAll();
        long sink = 0;
        for (User u : users) {
            sink += u.getOrders().size();
        }
        if (sink < 0) throw new IllegalStateException();
    }

    private List<QueryInfo> runWorkloadWithCapture() {
        em.clear();
        CapturedQueries.start();
        List<User> users = userRepository.findAll();
        long sink = 0;
        for (User u : users) {
            sink += u.getOrders().size();
        }
        if (sink < 0) throw new IllegalStateException();
        return CapturedQueries.stop();
    }

    private static long time(int iterations, Runnable r) {
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            r.run();
        }
        return System.nanoTime() - start;
    }

    private void seed() {
        LocalDateTime base = LocalDateTime.now().minusDays(30);
        for (int u = 0; u < 5; u++) {
            User user = new User("User " + u, "user" + u + "@example.com");
            for (int o = 0; o < 3; o++) {
                user.addOrder(new Order("Product-" + u + "-" + o,
                        new BigDecimal("19.99"), base.plusDays(o)));
            }
            em.persist(user);
        }
        em.flush();
        em.clear();
    }
}
