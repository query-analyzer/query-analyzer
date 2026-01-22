package io.queryanalyzer.core.detector;

import io.queryanalyzer.core.config.QueryAnalyzerConfig;
import io.queryanalyzer.core.model.IssueType;
import io.queryanalyzer.core.model.QueryInfo;
import io.queryanalyzer.core.model.QueryIssue;
import io.queryanalyzer.core.model.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlowQueryDetectorTest {

    private SlowQueryDetector detector;
    private QueryAnalyzerConfig config;

    @BeforeEach
    void setUp() {
        config = new QueryAnalyzerConfig();
        detector = new SlowQueryDetector(config);
    }

    @Test
    void shouldDetectSlowQuery() {
        QueryInfo query = createQuery("SELECT * FROM users", 600);

        List<QueryIssue> issues = detector.detect(List.of(query));

        assertThat(issues).hasSize(1);
        QueryIssue issue = issues.get(0);
        assertThat(issue.getType()).isEqualTo(IssueType.SLOW_QUERY);
        assertThat(issue.getSeverity()).isEqualTo(Severity.ERROR);
        assertThat(issue.getDescription()).contains("600ms");
        assertThat(issue.getSuggestions()).isNotEmpty();
    }

    @Test
    void shouldNotDetectFastQuery() {
        QueryInfo query = createQuery("SELECT * FROM users", 50);

        List<QueryIssue> issues = detector.detect(List.of(query));

        assertThat(issues).isEmpty();
    }
    
    @Test
    void shouldNotDetectQueryJustBelowThreshold() {
        QueryInfo query = createQuery("SELECT * FROM users", 199);

        List<QueryIssue> issues = detector.detect(List.of(query));

        assertThat(issues).isEmpty();
    }
    
    @Test
    void shouldDetectQueryExactlyAtThreshold() {
        QueryInfo query = createQuery("SELECT * FROM users", 200);

        List<QueryIssue> issues = detector.detect(List.of(query));

        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).getSeverity()).isEqualTo(Severity.WARNING);
    }

    @Test
    void shouldDetectMultipleSlowQueries() {
        List<QueryInfo> queries = List.of(
            createQuery("SELECT * FROM users", 300),
            createQuery("SELECT * FROM orders", 400),
            createQuery("SELECT * FROM products", 250)
        );

        List<QueryIssue> issues = detector.detect(queries);

        assertThat(issues).hasSize(3);
    }

    @Test
    void shouldAssignCorrectSeverity() {
        QueryInfo warningQuery = createQuery("SELECT 1", 250);
        QueryInfo errorQuery = createQuery("SELECT 2", 600);
        QueryInfo criticalQuery = createQuery("SELECT 3", 2500);

        List<QueryIssue> issues = detector.detect(
            List.of(warningQuery, errorQuery, criticalQuery)
        );

        assertThat(issues).hasSize(3);
        assertThat(issues.get(0).getSeverity()).isEqualTo(Severity.WARNING);
        assertThat(issues.get(1).getSeverity()).isEqualTo(Severity.ERROR);
        assertThat(issues.get(2).getSeverity()).isEqualTo(Severity.CRITICAL);
    }

    @Test
    void shouldProvideSelectStarSuggestion() {
        QueryInfo query = createQuery("SELECT * FROM users WHERE id = 1", 300);

        List<QueryIssue> issues = detector.detect(List.of(query));

        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).getSuggestions())
            .anyMatch(s -> s.contains("SELECT *"));
    }

    @Test
    void shouldProvideLikeWildcardSuggestion() {
        QueryInfo query = createQuery("SELECT * FROM users WHERE name LIKE '%john%'", 400);

        List<QueryIssue> issues = detector.detect(List.of(query));

        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).getSuggestions())
            .anyMatch(s -> s.contains("wildcard"));
    }
    
    @Test
    void shouldDetectLeadingWildcardWithSpaces() {
        QueryInfo query = createQuery("SELECT * FROM users WHERE name LIKE  '%test'", 400);

        List<QueryIssue> issues = detector.detect(List.of(query));

        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).getSuggestions())
            .anyMatch(s -> s.contains("wildcard"));
    }
    
    @Test
    void shouldDetectOrConditionAsWord() {
        QueryInfo query = createQuery("SELECT * FROM users WHERE status = 'active' OR role = 'admin'", 300);

        List<QueryIssue> issues = detector.detect(List.of(query));

        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).getSuggestions())
            .anyMatch(s -> s.contains("OR conditions"));
    }
    
    @Test
    void shouldNotFalsePositiveOnWordsContainingOr() {
        QueryInfo query = createQuery("SELECT * FROM orders WHERE vendor_id = 1", 300);

        List<QueryIssue> issues = detector.detect(List.of(query));

        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).getSuggestions())
            .noneMatch(s -> s.contains("OR conditions"));
    }
    
    @Test
    void shouldDetectJoinSuggestion() {
        QueryInfo query = createQuery("SELECT u.* FROM users u JOIN orders o ON u.id = o.user_id", 300);

        List<QueryIssue> issues = detector.detect(List.of(query));

        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).getSuggestions())
            .anyMatch(s -> s.contains("JOIN"));
    }
    
    @Test
    void shouldDetectOrderByWithoutLimit() {
        QueryInfo query = createQuery("SELECT * FROM users ORDER BY created_at DESC", 300);

        List<QueryIssue> issues = detector.detect(List.of(query));

        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).getSuggestions())
            .anyMatch(s -> s.contains("ORDER BY") && s.contains("LIMIT"));
    }
    
    @Test
    void shouldNotSuggestLimitWhenPresent() {
        QueryInfo query = createQuery("SELECT * FROM users ORDER BY created_at DESC LIMIT 10", 300);

        List<QueryIssue> issues = detector.detect(List.of(query));

        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).getSuggestions())
            .noneMatch(s -> s.contains("ORDER BY") && s.contains("LIMIT"));
    }

    @Test
    void shouldHandleEmptyList() {
        List<QueryIssue> issues = detector.detect(Collections.emptyList());

        assertThat(issues).isEmpty();
    }

    @Test
    void shouldHandleNullList() {
        List<QueryIssue> issues = detector.detect(null);

        assertThat(issues).isEmpty();
    }

    @Test
    void shouldUseCustomThresholds() {
        QueryAnalyzerConfig customConfig = new QueryAnalyzerConfig(10, 50, 100, 200);
        SlowQueryDetector customDetector = new SlowQueryDetector(customConfig);
        QueryInfo query = createQuery("SELECT 1", 150);

        List<QueryIssue> issues = customDetector.detect(List.of(query));

        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).getSeverity()).isEqualTo(Severity.ERROR);
    }
    
    @Test
    void shouldThrowOnNullConfig() {
        assertThatThrownBy(() -> new SlowQueryDetector(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("null");
    }
    
    @Test
    void shouldIncludeMetrics() {
        QueryInfo query = createQuery("SELECT * FROM users", 500);

        List<QueryIssue> issues = detector.detect(List.of(query));

        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).getMetrics()).isNotNull();
        assertThat(issues.get(0).getMetrics().getExecutionTimeMs()).isEqualTo(500);
        assertThat(issues.get(0).getMetrics().getQueryCount()).isEqualTo(1);
    }
    
    @Test
    void shouldIncludeSampleQuery() {
        String sql = "SELECT id, name FROM users WHERE status = 'active'";
        QueryInfo query = createQuery(sql, 300);

        List<QueryIssue> issues = detector.detect(List.of(query));

        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).getSampleQuery()).isEqualTo(sql);
    }
    
    @Test
    void shouldDetectDeleteWithoutWhere() {
        QueryInfo query = createQuery("DELETE FROM temp_data", 300);

        List<QueryIssue> issues = detector.detect(List.of(query));

        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).getSuggestions())
            .anyMatch(s -> s.contains("UPDATE/DELETE") && s.contains("without WHERE"));
    }
    
    @Test
    void shouldNotWarnDeleteWithWhere() {
        QueryInfo query = createQuery("DELETE FROM temp_data WHERE created_at < '2024-01-01'", 300);

        List<QueryIssue> issues = detector.detect(List.of(query));

        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).getSuggestions())
            .noneMatch(s -> s.contains("DELETE") && s.contains("WHERE") && s.contains("without"));
    }

    private QueryInfo createQuery(String sql, long executionTimeMs) {
        return new QueryInfo(
            sql,
            sql.toLowerCase(),
            executionTimeMs,
            Instant.now(),
            new StackTraceElement[]{
                new StackTraceElement("com.example.UserService", "findUsers", "UserService.java", 25)
            },
            Thread.currentThread().getName(),
            null
        );
    }
}
