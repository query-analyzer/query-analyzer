package io.queryanalyzer.core.detector.report;

import io.queryanalyzer.core.model.ConfidenceScore;
import io.queryanalyzer.core.model.NPlusOneReport;
import io.queryanalyzer.core.model.QueryInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NPlusOneReportBuilderTest {

    private NPlusOneReportBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new NPlusOneReportBuilder();
    }

    @Test
    void testBuildReport() {
        List<QueryInfo> queries = createQueries(5, 10L);
        String location = "UserService.getOrders:42";
        String tableName = "orders";
        String endpoint = "/api/users";
        ConfidenceScore confidence = createConfidence(0.95);
        
        NPlusOneReport report = builder.build(queries, location, tableName, endpoint, confidence);
        
        assertNotNull(report);
        assertEquals(5, report.getQueryCount());
        assertEquals(location, report.getLocation());
        assertEquals(tableName, report.getTableName());
        assertEquals(endpoint, report.getEndpoint());
        assertEquals(50L, report.getTotalTimeMs());
    }

    @Test
    void testCalculateMetricsWithPrecision() {
        List<QueryInfo> queries = createQueries(3, 10L);
        
        NPlusOneReport report = builder.build(queries, "location", "table", null, null);
        
        assertEquals(30L, report.getTotalTimeMs());
        assertEquals(10L, report.getAverageTimeMs());
    }

    @Test
    void testCalculatePotentialImprovement() {
        List<QueryInfo> queries = createQueries(10, 5L);
        
        NPlusOneReport report = builder.build(queries, "location", "table", null, null);
        
        assertEquals(50L, report.getTotalTimeMs());
        assertTrue(report.getPotentialImprovementPercent() > 70.0);
        assertTrue(report.getPotentialImprovementPercent() < 90.0);
    }

    @Test
    void testExtractSampleQueries() {
        List<QueryInfo> queries = createQueries(10, 5L);
        
        NPlusOneReport report = builder.build(queries, "location", "table", null, null);
        
        assertEquals(3, report.getSampleQueries().size());
    }

    @Test
    void testExtractSampleQueriesWithLongSQL() {
        String longSql = "SELECT * FROM orders WHERE " + "x".repeat(100);
        List<QueryInfo> queries = List.of(
            createQueryWithSql(longSql, 5L)
        );
        
        NPlusOneReport report = builder.build(queries, "location", "table", null, null);
        
        String sample = report.getSampleQueries().get(0);
        assertTrue(sample.length() <= 80);
        assertTrue(sample.endsWith("..."));
    }

    @Test
    void testFormatForConsoleWithConfidence() {
        List<QueryInfo> queries = createQueries(5, 10L);
        ConfidenceScore confidence = createConfidence(0.94);
        NPlusOneReport report = builder.build(queries, "UserService:42", "orders", "/api/users", confidence);
        
        List<String> output = builder.formatForConsole(report, confidence);
        
        assertFalse(output.isEmpty());
        assertTrue(output.stream().anyMatch(line -> line.contains("Confidence:")));
    }

    @Test
    void testFormatWithoutConfidenceReturnsEmpty() {
        List<QueryInfo> queries = createQueries(3, 10L);
        NPlusOneReport report = builder.build(queries, "location", "orders", null, null);
        
        List<String> output = builder.formatForConsole(report, null);
        
        assertTrue(output.isEmpty());
    }

    @Test
    void testConfidenceLevelFormatting() {
        List<QueryInfo> queries = createQueries(5, 10L);
        ConfidenceScore confidence = createConfidence(0.95);
        NPlusOneReport report = builder.build(queries, "location", "orders", null, confidence);
        
        List<String> output = builder.formatForConsole(report, confidence);
        
        assertTrue(output.stream().anyMatch(line -> line.contains("HIGH") && line.contains("95%")));
    }

    @Test
    void testConfidenceReasoningShown() {
        ConfidenceScore confidence = ConfidenceScore.builder()
                .overallScore(0.94)
                .stackTraceScore(0.9)
                .timingScore(0.8)
                .patternScore(1.0)
                .reasoning("Hibernate lazy loading detected")
                .build();
        
        List<QueryInfo> queries = createQueries(5, 10L);
        NPlusOneReport report = builder.build(queries, "location", "orders", null, confidence);
        
        List<String> output = builder.formatForConsole(report, confidence);
        
        assertTrue(output.stream().anyMatch(line -> line.contains("Hibernate lazy loading detected")));
    }

    @Test
    void testZeroExecutionTimeDoesNotCauseDivisionByZero() {
        List<QueryInfo> queries = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            queries.add(new QueryInfo(
                "SELECT * FROM fast_cache WHERE id = ?",
                "select * from fast_cache where id = ?",
                0L,
                Instant.now(),
                new StackTraceElement[0],
                "thread-1",
                null
            ));
        }
        
        NPlusOneReport report = builder.build(queries, "CacheService:42", "fast_cache", null, null);
        
        assertNotNull(report);
        assertEquals(0L, report.getTotalTimeMs());
        assertEquals(0L, report.getAverageTimeMs());
        assertEquals(0.0, report.getPotentialImprovementPercent());
        assertEquals(0L, report.getPotentialSavingsMs());
    }

    @Test
    void testMixedZeroAndNonZeroExecutionTimes() {
        List<QueryInfo> queries = new ArrayList<>();
        queries.add(createQueryWithSql("SELECT * FROM cache", 0L));
        queries.add(createQueryWithSql("SELECT * FROM cache", 0L));
        queries.add(createQueryWithSql("SELECT * FROM cache", 10L));
        
        NPlusOneReport report = builder.build(queries, "location", "cache", null, null);
        
        assertEquals(10L, report.getTotalTimeMs());
        assertTrue(report.getAverageTimeMs() > 0);
    }

    // Helper methods

    private List<QueryInfo> createQueries(int count, long executionTime) {
        List<QueryInfo> queries = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            queries.add(createQueryWithSql("SELECT * FROM orders WHERE user_id = ?", executionTime));
        }
        return queries;
    }

    private QueryInfo createQueryWithSql(String sql, long executionTime) {
        return new QueryInfo(
            sql,
            sql,
            executionTime,
            Instant.now(),
            new StackTraceElement[0],
            "thread-1",
            null
        );
    }

    private ConfidenceScore createConfidence(double score) {
        return ConfidenceScore.builder()
                .overallScore(score)
                .stackTraceScore(0.9)
                .timingScore(0.8)
                .patternScore(1.0)
                .reasoning("Test reasoning")
                .build();
    }
}
