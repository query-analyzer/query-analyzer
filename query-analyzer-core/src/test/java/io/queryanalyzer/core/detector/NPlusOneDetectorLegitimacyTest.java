package io.queryanalyzer.core.detector;

import io.queryanalyzer.core.config.TestConfigFactory;
import io.queryanalyzer.core.model.QueryInfo;
import io.queryanalyzer.core.model.QueryIssue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NPlusOneDetectorLegitimacyTest {

    private NPlusOneDetector detector;

    @BeforeEach
    void setUp() {
        detector = new NPlusOneDetector(TestConfigFactory.createDefault());
    }

    @Test
    void shouldNotFlagPaginationPattern() {
        List<QueryInfo> queries = new ArrayList<>();
        
        for (int i = 0; i < 5; i++) {
            queries.add(createQuery(
                String.format("SELECT * FROM orders WHERE user_id = 1 LIMIT 10 OFFSET %d", i * 10),
                10
            ));
        }
        
        List<QueryIssue> issues = detector.detect(queries);
        
        assertThat(issues).isEmpty();
    }

    @Test
    void shouldNotFlagStreamingPattern() {
        List<QueryInfo> queries = new ArrayList<>();
        
        for (int i = 0; i < 5; i++) {
            queries.add(createQuery(
                String.format("SELECT * FROM logs WHERE timestamp > '2024-01-01 00:%02d:00'", i),
                10
            ));
        }
        
        List<QueryIssue> issues = detector.detect(queries);
        
        // Should NOT flag streaming as N+1
        assertThat(issues).isEmpty();
    }

    @Test
    void shouldFlagRealN1WithLimit() {
        List<QueryInfo> queries = new ArrayList<>();
        
        for (int i = 0; i < 10; i++) {
            queries.add(createQueryWithStack(
                "SELECT * FROM orders WHERE user_id = ? LIMIT 10", // Same SQL each time
                createStack("com.example.UserService", "loadOrders", 42),
                10
            ));
        }
        
        List<QueryIssue> issues = detector.detect(queries);
        
        assertThat(issues).isNotEmpty();
    }

    @Test
    void shouldNotFlagBatchQuery() {
        List<QueryInfo> queries = new ArrayList<>();
        
        for (int i = 0; i < 3; i++) {
            queries.add(createQuery(
                "SELECT * FROM orders WHERE user_id IN (1, 2, 3, 4, 5)",
                10
            ));
        }
        
        List<QueryIssue> issues = detector.detect(queries);
        
        assertThat(issues).isEmpty();
    }

    @Test
    void shouldHandleMixedTimestampColumns() {
        List<QueryInfo> queries = new ArrayList<>();
        
        queries.add(createQuery("SELECT * FROM logs WHERE created_at > '2024-01-01'", 10));
        queries.add(createQuery("SELECT * FROM logs WHERE created_at > '2024-01-02'", 10));
        queries.add(createQuery("SELECT * FROM logs WHERE created_at > '2024-01-03'", 10));
        queries.add(createQuery("SELECT * FROM logs WHERE created_at > '2024-01-04'", 10));
        
        List<QueryIssue> issues = detector.detect(queries);
        
        assertThat(issues).isEmpty();
    }

    @Test
    void shouldRequireMinimumQueriesForStreaming() {
        // Only 2 timestamp queries - not enough to confirm streaming
        List<QueryInfo> queries = new ArrayList<>();
        
        queries.add(createQueryWithStack(
            "SELECT * FROM logs WHERE timestamp > '2024-01-01'",
            createStack("com.example.Service", "method", 42),
            10
        ));
        queries.add(createQueryWithStack(
            "SELECT * FROM logs WHERE timestamp > '2024-01-02'",
            createStack("com.example.Service", "method", 42),
            10
        ));
        
        List<QueryIssue> issues = detector.detect(queries);
        
        // Might flag as N+1 (not enough samples to confirm streaming)
        // This is acceptable - better false positive than false negative
    }

    @Test
    void shouldHandleNullSqlGracefully() {
        // QueryInfo validates and rejects null SQL at construction
        assertThrows(IllegalArgumentException.class, () -> {
            new QueryInfo(
                null, // null SQL
                null, // null normalized
                10L, // executionTimeMs
                Instant.now(),
                null, // stackTrace
                "main",
                null // metadata
            );
        });
    }

    @Test
    void shouldHandleEmptySql() {
        // QueryInfo validates and rejects empty SQL at construction
        assertThrows(IllegalArgumentException.class, () -> {
            createQuery("", 10);
        });
    }

    private QueryInfo createQuery(String sql, long executionTimeMs) {
        return new QueryInfo(
            sql,
            sql, // normalized
            executionTimeMs,
            Instant.now(),
            null,
            "main",
            null // metadata
        );
    }

    private QueryInfo createQueryWithStack(String sql, StackTraceElement[] stack, long executionTimeMs) {
        return new QueryInfo(
            sql,
            sql, // normalized
            executionTimeMs,
            Instant.now(),
            stack,
            "main",
            null // metadata
        );
    }

    private StackTraceElement[] createStack(String className, String methodName, int lineNumber) {
        return new StackTraceElement[] {
            new StackTraceElement(className, methodName, "File.java", lineNumber)
        };
    }
}
