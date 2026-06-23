package io.queryanalyzer.benchmark;

import io.queryanalyzer.benchmark.capture.CapturedQueries;
import io.queryanalyzer.benchmark.model.Order;
import io.queryanalyzer.benchmark.model.User;
import io.queryanalyzer.benchmark.repository.OrderRepository;
import io.queryanalyzer.benchmark.repository.UserRepository;
import io.queryanalyzer.core.config.DetectorConfig;
import io.queryanalyzer.core.detector.NPlusOneDetector;
import io.queryanalyzer.core.model.QueryInfo;
import io.queryanalyzer.core.plan.QueryPlanAnalyzer;
import io.queryanalyzer.core.plan.QueryPlanAnalyzerFactory;
import io.queryanalyzer.core.plan.model.QueryPlanResult;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY;
import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * RQ4 on a REAL database. The H2 overhead figure ("0.28 ms / below noise") invites the
 * fair objection that an in-memory engine hides the only thing that matters in production:
 * the cost of query-analyzer <em>relative</em> to a real query round trip. This test boots
 * an actual PostgreSQL instance (zonky embedded-postgres: a real {@code postgres} binary run
 * as the current user, no Docker, no root) and reports the capture + analysis overhead as a
 * <em>fraction of a genuine PostgreSQL request</em>.
 *
 * <p>It also exercises the tool's own {@link QueryPlanAnalyzer} (PostgreSQL backend) against
 * the live engine, turning the paper's "EXPLAIN-based plan analysis (PostgreSQL)" claim from
 * an asserted capability into a demonstrated one.</p>
 *
 * <p>Report-only, like the other benchmark tests. Run with:
 * {@code mvn -pl query-analyzer-benchmark test -Dtest=OverheadAndPlanPostgresTest}.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@AutoConfigureEmbeddedDatabase(type = POSTGRES, provider = ZONKY)
class OverheadAndPlanPostgresTest {

    @Autowired
    private TestEntityManager em;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private DataSource dataSource;

    private static final int WARMUP = 50;
    private static final int ITERATIONS = 300;

    @Test
    void measureOverheadAndPlanOnRealPostgres() throws Exception {
        seed();

        DetectorConfig cfg = DetectorConfig.builder()
                .detectionMode(DetectorConfig.DetectionMode.CONFIDENCE).minRepetitions(3).build();

        for (int i = 0; i < WARMUP; i++) {
            runWorkloadNoCapture();
            List<QueryInfo> t = runWorkloadWithCapture();
            new NPlusOneDetector(cfg).detect(t);
        }

        long baselineNs = time(ITERATIONS, this::runWorkloadNoCapture);
        long captureNs = time(ITERATIONS, this::runWorkloadWithCapture);

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
        double totalOverhead = captureOverhead + anaMs;
        double relPct = baseMs > 0 ? (totalOverhead / baseMs) * 100.0 : 0;

        System.out.println("\n========= OVERHEAD ON REAL POSTGRESQL =========");
        System.out.printf("Real PG request (no capture) : %.4f ms/req (%d-query trace)%n", baseMs, trace.size());
        System.out.printf("Request + capture hook       : %.4f ms/req%n", capMs);
        System.out.printf("  -> capture overhead        : %.4f ms/req%n", captureOverhead);
        System.out.printf("Post-request analysis        : %.4f ms/req%n", anaMs);
        System.out.printf("Total query-analyzer overhead: %.4f ms/req%n", totalOverhead);
        System.out.printf("RELATIVE overhead            : %.2f%% of a real PG request%n", relPct);
        System.out.println("===============================================\n");

        // ---- Tool's own EXPLAIN-based plan analysis against the live PostgreSQL ----
        QueryPlanAnalyzerFactory factory = new QueryPlanAnalyzerFactory();
        try (Connection conn = dataSource.getConnection()) {
            Optional<QueryPlanAnalyzer> analyzer = factory.getAnalyzer(conn);
            System.out.println("========= TOOL EXPLAIN PLAN (PostgreSQL) =========");
            System.out.println("Detected DB type: "
                    + factory.detectDatabaseType(conn) + " | analyzer present: " + analyzer.isPresent());

            // The N+1 child query shape petclinic/JPA emit per parent row.
            String childQuery = "select * from orders where user_id = ?";
            QueryPlanResult plan = analyzer
                    .map(a -> a.analyze(conn, childQuery))
                    .orElse(null);

            assertNotNull(plan, "PostgreSQL plan analyzer returned no plan for the N+1 child query");
            System.out.println("Query    : " + childQuery);
            System.out.println("Summary  : " + plan.getSummary());
            System.out.println("AccessType: " + plan.getAccessType()
                    + " | usesIndex=" + plan.isUsesIndex()
                    + " | fullTableScan=" + plan.isFullTableScan()
                    + " | estRows=" + plan.getEstimatedRows()
                    + " | severity=" + plan.getSeverityScore());
            System.out.println("Recommendations: " + plan.getRecommendations());
            System.out.println("--- raw EXPLAIN ---");
            System.out.println(plan.getRawPlan());
            System.out.println("==================================================\n");
        }
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
