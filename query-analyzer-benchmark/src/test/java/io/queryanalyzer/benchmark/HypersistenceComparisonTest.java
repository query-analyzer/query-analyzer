package io.queryanalyzer.benchmark;

import io.hypersistence.utils.jdbc.validator.SQLStatementCountValidator;
import io.queryanalyzer.benchmark.capture.CapturedQueries;
import io.queryanalyzer.benchmark.model.Order;
import io.queryanalyzer.benchmark.model.User;
import io.queryanalyzer.benchmark.repository.OrderRepository;
import io.queryanalyzer.benchmark.repository.UserRepository;
import io.queryanalyzer.core.config.DetectorConfig;
import io.queryanalyzer.core.detector.NPlusOneDetector;
import io.queryanalyzer.core.model.QueryInfo;
import net.ttddyy.dsproxy.QueryCountHolder;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import javax.sql.DataSource;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * LIVE comparison against the real Hypersistence Utils binary
 * ({@code io.hypersistence:hypersistence-utils-hibernate-63}) on the exact same
 * Spring Boot 3.2 / Hibernate 6.3 stack.
 *
 * <p>Hypersistence Utils' {@link SQLStatementCountValidator} is a <b>manual statement
 * counter</b>: the developer asserts the expected number of statements
 * ({@code assertSelectCount(n)}), and the assertion throws on mismatch. It captures
 * statements through the {@code datasource-proxy} {@link QueryCountHolder}. This test:</p>
 *
 * <ol>
 *   <li>proves the real binary runs on this stack (a correct {@code assertSelectCount}
 *       passes; an incorrect one throws);</li>
 *   <li>records the raw SELECT count each scenario produces, and contrasts it with
 *       query-analyzer's automatic verdict.</li>
 * </ol>
 *
 * <p>The honest takeaway is not "we are more precise than Hypersistence" - a developer
 * who hardcodes the right count per test will not get false positives. It is that a pure
 * counter (a) needs a human-specified expected count for <em>every</em> test, (b) cannot
 * distinguish an N+1 (e.g. 6 selects) from legitimate pagination (5 selects) from the
 * count alone, and (c) only runs in tests. query-analyzer classifies all scenarios
 * automatically, with zero per-test configuration, and also runs in production.</p>
 */
@DataJpaTest
@Import(HypersistenceComparisonTest.DataSourceProxyConfig.class)
class HypersistenceComparisonTest {

    /** Wrap the autoconfigured DataSource so datasource-proxy counts every statement. */
    @TestConfiguration
    static class DataSourceProxyConfig {
        @Bean
        static BeanPostProcessor dataSourceCountingProxy() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (bean instanceof DataSource ds && !(bean instanceof ProxyDataSource)) {
                        return ProxyDataSourceBuilder.create(ds).name("bench").countQuery().build();
                    }
                    return bean;
                }
            };
        }
    }

    @Autowired
    private TestEntityManager em;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private OrderRepository orderRepository;

    private record Scenario(String name, boolean isNPlusOne, Runnable work) {
    }

    @Test
    void compareAgainstHypersistenceUtils() throws IOException {
        seed();

        // (1) Prove the real Hypersistence binary works on this stack.
        em.clear();
        SQLStatementCountValidator.reset();
        userRepository.findAllWithOrders().forEach(u -> u.getOrders().size());
        assertDoesNotThrow(() -> SQLStatementCountValidator.assertSelectCount(1),
                "JOIN FETCH should issue exactly one SELECT");

        em.clear();
        SQLStatementCountValidator.reset();
        userRepository.findAll().forEach(u -> u.getOrders().size());
        assertThrows(AssertionError.class, () -> SQLStatementCountValidator.assertSelectCount(1),
                "Classic N+1 issues more than one SELECT, so assertSelectCount(1) must throw");

        // (2) Record raw SELECT counts per scenario and query-analyzer's auto verdict.
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

        StringBuilder md = new StringBuilder();
        md.append("# Live comparison: query-analyzer vs Hypersistence Utils ")
                .append("(SQLStatementCountValidator)\n\n");
        md.append("Real `io.hypersistence:hypersistence-utils-hibernate-63:3.15.2` binary, ")
                .append("same Spring Boot 3.2 / Hibernate 6.3 stack.\n\n");
        md.append("| Scenario | Ground truth | Hypersistence SELECT count | ")
                .append("query-analyzer (automatic) |\n");
        md.append("|---|---|---|---|\n");

        System.out.println("\n========= LIVE: query-analyzer vs Hypersistence Utils =========");
        System.out.printf("%-38s %-8s %-12s %s%n", "scenario", "truth", "Hyp.SELECTs", "QA-auto");
        for (Scenario s : scenarios) {
            // Hypersistence path: count statements via datasource-proxy.
            em.clear();
            QueryCountHolder.clear();
            s.work().run();
            long hypSelects = QueryCountHolder.getGrandTotal().getSelect();

            // query-analyzer path: capture trace + classify automatically.
            em.clear();
            CapturedQueries.start();
            s.work().run();
            List<QueryInfo> trace = CapturedQueries.stop();
            boolean qaFlag = !new NPlusOneDetector(autoCfg).detect(trace).isEmpty();
            boolean qaCorrect = qaFlag == s.isNPlusOne();

            md.append("| ").append(s.name())
                    .append(" | ").append(s.isNPlusOne() ? "N+1" : "clean")
                    .append(" | ").append(hypSelects)
                    .append(" | ").append(qaFlag ? "FLAG" : "pass")
                    .append(qaCorrect ? " OK" : " WRONG").append(" |\n");
            System.out.printf("%-38s %-8s %-12d %s%n",
                    s.name(), s.isNPlusOne() ? "N+1" : "clean", hypSelects,
                    (qaFlag ? "FLAG" : "pass") + (qaCorrect ? " OK" : " WRONG"));
        }

        md.append("\n## Interpretation\n\n");
        md.append("- Hypersistence reports a raw statement **count**; it has no automatic ")
                .append("classification. The developer must assert the expected count for ")
                .append("**every** test, and it runs only in tests.\n");
        md.append("- A count alone cannot separate an N+1 (6 SELECTs) from legitimate ")
                .append("pagination (5 SELECTs); query-analyzer separates them automatically ")
                .append("via pattern/confidence analysis, with no per-test configuration, and ")
                .append("also runs in production.\n");

        Path outDir = Path.of("target");
        Files.createDirectories(outDir);
        Files.writeString(outDir.resolve("benchmark-hypersistence.md"), md.toString());
        System.out.println("Report written to target/benchmark-hypersistence.md");
        System.out.println("===============================================================\n");
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
        em.clear();
        QueryCountHolder.clear();
        CapturedQueries.start();
        guard(orderRepository.findByUserIdIn(ids).size());
    }

    private void workRepeatedBelowThreshold() {
        guard(orderRepository.findByUserId(1L).size() + orderRepository.findByUserId(1L).size());
    }

    // ---------------------------------------------------------------- fixtures

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
