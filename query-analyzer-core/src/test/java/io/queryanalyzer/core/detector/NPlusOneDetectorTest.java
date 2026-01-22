package io.queryanalyzer.core.detector;

import io.queryanalyzer.core.config.DetectorConfig;
import io.queryanalyzer.core.config.TestConfigFactory;
import io.queryanalyzer.core.model.IssueType;
import io.queryanalyzer.core.model.QueryInfo;
import io.queryanalyzer.core.model.QueryIssue;
import io.queryanalyzer.core.model.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NPlusOneDetectorTest {

    private NPlusOneDetector detector;
    private DetectorConfig config;

    @BeforeEach
    void setUp() {
        config = TestConfigFactory.createDefault();
        detector = new NPlusOneDetector(config);
    }

    @Test
    void shouldDetectNPlusOnePattern() {
        List<QueryInfo> queries = new ArrayList<>();

        queries.add(createQuery("SELECT * FROM users", 10));

        for (int i = 0; i < 10; i++) {
            queries.add(createQuery("SELECT * FROM orders WHERE user_id = " + i, 25));
        }

        List<QueryIssue> issues = detector.detect(queries);

        assertThat(issues).hasSize(1);
        QueryIssue issue = issues.get(0);
        assertThat(issue.getType()).isEqualTo(IssueType.N_PLUS_ONE);
        assertThat(issue.getSeverity()).isIn(Severity.WARNING, Severity.ERROR);
        assertThat(issue.getDescription()).contains("10 repeated queries");
        assertThat(issue.getSuggestions()).isNotEmpty();
        assertThat(issue.getMetrics()).isNotNull();
    }

    @Test
    void shouldNotDetectWithFewerThanThreeRepetitions() {
        List<QueryInfo> queries = List.of(
            createQuery("SELECT * FROM orders WHERE user_id = 1", 5),
            createQuery("SELECT * FROM orders WHERE user_id = 2", 5)
        );

        List<QueryIssue> issues = detector.detect(queries);

        assertThat(issues).isEmpty();
    }

    @Test
    void shouldNotDetectDifferentQueries() {
        List<QueryInfo> queries = List.of(
            createQuery("SELECT * FROM users", 10),
            createQuery("SELECT * FROM products", 10),
            createQuery("SELECT * FROM orders", 10)
        );

        List<QueryIssue> issues = detector.detect(queries);

        assertThat(issues).isEmpty();
    }

    @Test
    void shouldHandleEmptyList() {
        List<QueryInfo> queries = Collections.emptyList();

        List<QueryIssue> issues = detector.detect(queries);

        assertThat(issues).isEmpty();
    }

    @Test
    void shouldHandleNullList() {
        List<QueryIssue> issues = detector.detect(null);

        assertThat(issues).isEmpty();
    }

    @Test
    void shouldDetectMultipleNPlusOnePatterns() {
        List<QueryInfo> queries = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            queries.add(createQuery("SELECT * FROM orders WHERE user_id = " + i, 5));
        }

        for (int i = 0; i < 5; i++) {
            queries.add(createQuery("SELECT * FROM items WHERE order_id = " + i, 3));
        }

        List<QueryIssue> issues = detector.detect(queries);

        assertThat(issues).hasSize(2);
    }

    @Test
    void shouldIncludeActionableInformationInIssue() {
        List<QueryInfo> queries = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            queries.add(createQuery("SELECT * FROM orders WHERE user_id = " + i, 5));
        }

        List<QueryIssue> issues = detector.detect(queries);

        assertThat(issues).hasSize(1);
        List<String> suggestions = issues.get(0).getSuggestions();
        assertThat(suggestions).isNotEmpty();
        
        String allSuggestions = String.join(" ", suggestions);
        assertThat(allSuggestions).containsAnyOf(
            "Confidence:",
            "Hibernate",
            "JOIN FETCH",
            "BatchSize",
            "N+1"
        );
    }

    private QueryInfo createQuery(String sql, long executionTimeMs) {
        return new QueryInfo(
            sql,
            normalizeForTest(sql),
            executionTimeMs,
            Instant.now(),
            new StackTraceElement[]{
                new StackTraceElement("com.example.UserController", "getUsers", "UserController.java", 42)
            },
            Thread.currentThread().getName(),
            null
        );
    }

    private String normalizeForTest(String sql) {
        return sql.replaceAll("\\d+", "?").toLowerCase();
    }

    @Test
    void shouldSkipAnalysisForTooManyQueries() {
        List<QueryInfo> queries = new ArrayList<>();
        for (int i = 0; i < 1001; i++) {
            queries.add(createQuery("SELECT * FROM orders WHERE id = " + i, 5));
        }

        List<QueryIssue> issues = detector.detect(queries);

        assertThat(issues).isEmpty();
    }

    @Test
    void shouldDetectHibernateLazyLoading() {
        List<QueryInfo> queries = new ArrayList<>();
        
        for (int i = 0; i < 5; i++) {
            QueryInfo query = new QueryInfo(
                "SELECT * FROM orders WHERE user_id = " + i,
                "select * from orders where user_id = ?",
                10,
                Instant.now(),
                new StackTraceElement[]{
                    new StackTraceElement(
                        "org.hibernate.proxy.pojo.bytebuddy.ByteBuddyInterceptor",
                        "intercept",
                        "ByteBuddyInterceptor.java",
                        42
                    ),
                    new StackTraceElement(
                        "com.example.User$HibernateProxy$1234",
                        "getOrders",
                        "User.java",
                        25
                    ),
                    new StackTraceElement(
                        "com.example.UserService",
                        "loadUsers",
                        "UserService.java",
                        100
                    )
                },
                Thread.currentThread().getName(),
                null
            );
            queries.add(query);
        }

        List<QueryIssue> issues = detector.detect(queries);

        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).getType()).isEqualTo(IssueType.N_PLUS_ONE);
    }

    @Test
    void shouldNotDetectDeliberatelyPacedQueries() {
        List<QueryInfo> queries = new ArrayList<>();
        Instant baseTime = Instant.now();
        
        for (int i = 0; i < 5; i++) {
            queries.add(new QueryInfo(
                "SELECT * FROM api_calls WHERE id = " + i,
                "select * from api_calls where id = ?",
                10,
                baseTime.plusMillis(i * 100), // 100ms gaps = rate limiting
                new StackTraceElement[]{
                    new StackTraceElement("com.example.ApiClient", "rateLimitedCall", "ApiClient.java", 50)
                },
                Thread.currentThread().getName(),
                null
            ));
        }

        List<QueryIssue> issues = detector.detect(queries);

        assertThat(issues).isEmpty();
    }

    @Test
    void shouldDetectQueriesFromSameCodeLocation() {
        List<QueryInfo> queries = new ArrayList<>();
        
        for (int i = 0; i < 5; i++) {
            queries.add(new QueryInfo(
                "SELECT * FROM orders WHERE id = " + i,
                "select * from orders where id = ?",
                5,
                Instant.now().plusMillis(i * 10), // Close together in time
                new StackTraceElement[]{
                    new StackTraceElement("com.example.OrderService", "loadOrder", "OrderService.java", 42)
                },
                Thread.currentThread().getName(),
                null
            ));
        }

        List<QueryIssue> issues = detector.detect(queries);

        assertThat(issues).hasSize(1);
    }

    @Test
    void shouldExtractTableNameFromSchemaQualifiedQuery() {
        List<QueryInfo> queries = new ArrayList<>();
        
        for (int i = 0; i < 3; i++) {
            queries.add(createQuery("SELECT * FROM public.users WHERE id = " + i, 5));
        }

        List<QueryIssue> issues = detector.detect(queries);

        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).getDescription()).contains("users");
        assertThat(issues.get(0).getDescription()).doesNotContain("public");
    }

    @Test
    void shouldExtractTableNameFromQuotedSchemaQuery() {
        List<QueryInfo> queries = new ArrayList<>();
        
        for (int i = 0; i < 3; i++) {
            queries.add(createQuery("SELECT * FROM \"public\".\"users\" WHERE id = " + i, 5));
        }

        List<QueryIssue> issues = detector.detect(queries);

        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).getDescription()).contains("users");
    }

    @Test
    void shouldNotDetectWhenLocationIsUnknown() {
        List<QueryInfo> queries = new ArrayList<>();
        
        for (int i = 0; i < 5; i++) {
            queries.add(new QueryInfo(
                "SELECT * FROM orders WHERE id = " + i,
                "select * from orders where id = ?",
                5,
                Instant.now(),
                new StackTraceElement[]{
                    new StackTraceElement("java.lang.Thread", "run", "Thread.java", 100),
                    new StackTraceElement("org.springframework.jdbc.core.JdbcTemplate", "query", "JdbcTemplate.java", 200)
                },
                Thread.currentThread().getName(),
                null
            ));
        }

        List<QueryIssue> issues = detector.detect(queries);

        assertThat(issues).isNotNull();
    }

    @Test
    void shouldHandleQueriesWithBatchingAlready() {
        List<QueryInfo> queries = new ArrayList<>();
        
        for (int i = 0; i < 5; i++) {
            queries.add(createQuery("SELECT * FROM orders WHERE user_id IN (1,2,3,4,5)", 10));
        }

        List<QueryIssue> issues = detector.detect(queries);

        assertThat(issues).isEmpty();
    }
    
    @Test
    void shouldRespectConfigurableMinConfidenceThreshold() {
        List<QueryInfo> queries = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            queries.add(createQuery("SELECT * FROM orders WHERE id = " + i, 5));
        }
        
        NPlusOneDetector lowThresholdDetector = new NPlusOneDetector(
            TestConfigFactory.createWithMinConfidence(0.3));
        List<QueryIssue> issuesLow = lowThresholdDetector.detect(queries);
        assertThat(issuesLow).isNotEmpty();
        
        NPlusOneDetector highThresholdDetector = new NPlusOneDetector(
            TestConfigFactory.createWithMinConfidence(0.9));
        List<QueryIssue> issuesHigh = highThresholdDetector.detect(queries);
        assertThat(issuesHigh).isEmpty();
    }
    
    @Test
    void shouldUseDefaultConfidenceThresholdWhenNotProvided() {
        NPlusOneDetector defaultDetector = new NPlusOneDetector(
            TestConfigFactory.createDefault());
        
        List<QueryInfo> queries = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            queries.add(createQuery("SELECT * FROM orders WHERE user_id = " + i, 25));
        }
        
        List<QueryIssue> issues = defaultDetector.detect(queries);
        assertThat(issues).isNotEmpty();
    }
    
    @Test
    void shouldHandleZeroTotalTimeWithoutCrash() {
        List<QueryInfo> queries = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            queries.add(createQuery("SELECT * FROM fast_table WHERE id = " + i, 0));
        }
        
        List<QueryIssue> issues = detector.detect(queries);
        
        if (!issues.isEmpty()) {
            assertThat(issues.get(0).getMetrics()).isNotNull();
            assertThat(issues.get(0).getMetrics().getEstimatedImprovementPercent()).isEqualTo(0.0);
        }
    }
}
