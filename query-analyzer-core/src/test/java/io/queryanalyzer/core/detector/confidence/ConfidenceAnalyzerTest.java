package io.queryanalyzer.core.detector.confidence;
import io.queryanalyzer.core.config.TestConfigFactory;

import io.queryanalyzer.core.detector.timing.TimingAnalyzer;
import io.queryanalyzer.core.model.ConfidenceScore;
import io.queryanalyzer.core.model.QueryInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

import io.queryanalyzer.core.config.DetectorConfig;

class ConfidenceAnalyzerTest {

    private ConfidenceAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        DetectorConfig config = DetectorConfig.builder().build();
        analyzer = new ConfidenceAnalyzer(new TimingAnalyzer(config), config);
    }

    @Test
    void testWeightsAreValidated() {
        
        ConfidenceAnalyzer testAnalyzer = TestConfigFactory.createConfidenceAnalyzer();
        assertNotNull(testAnalyzer, "Analyzer should be created without throwing exception");

    }

    @Test
    void testAnalyzeWithLazyLoading() {
        List<QueryInfo> queries = createQueriesWithLazyLoading(5);
        
        ConfidenceScore score = analyzer.analyze(queries);
        
        // With lazy loading indicators, confidence should be at least medium
        assertTrue(score.getOverallScore() >= 0.3, "Should have reasonable confidence with lazy loading");
    }

    @Test
    void testAnalyzeWithoutLazyLoading() {
        List<QueryInfo> queries = createQueriesWithoutLazyLoading(5);
        
        ConfidenceScore score = analyzer.analyze(queries);
        
        assertEquals(0.0, score.getStackTraceScore(), "Stack trace score should be zero");
        assertNotNull(score.getReasoning());
    }

    @Test
    void testTightLoopHighConfidence() {
        List<QueryInfo> queries = createQuickQueries(10);
        
        ConfidenceScore score = analyzer.analyze(queries);
        
        assertTrue(score.getTimingScore() > 0.5, "Timing score should be high for tight loop");
    }

    @Test
    void testSameLocationHighConfidence() {
        List<QueryInfo> queries = createQueriesFromSameLocation(5);
        
        ConfidenceScore score = analyzer.analyze(queries);
        
        assertEquals(1.0, score.getPatternScore(), "Pattern score should be 1.0 for same location");
    }

    @Test
    void testLowConfidencePattern() {
        List<QueryInfo> queries = createLowConfidenceQueries(3);
        
        ConfidenceScore score = analyzer.analyze(queries);
        
        assertTrue(score.getOverallScore() < 0.9, "Should have lower confidence");
        assertFalse(score.isHighConfidence());
    }

    @Test
    void testEmptyQueriesReturnsZeroConfidence() {
        List<QueryInfo> queries = new ArrayList<>();
        
        ConfidenceScore score = analyzer.analyze(queries);
        
        assertEquals(0.0, score.getOverallScore());
        assertEquals("No queries to analyze", score.getReasoning());
    }

    @Test
    void testConfidenceLevels() {
        List<QueryInfo> highConfQueries = createQueriesWithLazyLoading(10);
        ConfidenceScore highScore = analyzer.analyze(highConfQueries);
        
        // Should produce some confidence score
        assertTrue(highScore.getOverallScore() >= 0.0);
        assertNotNull(highScore.getConfidenceLevel());
        assertTrue(List.of("HIGH", "MEDIUM", "LOW").contains(highScore.getConfidenceLevel()));
    }

    @Test
    void testConfidenceBreakdown() {
        List<QueryInfo> queries = createQueriesWithLazyLoading(5);
        
        ConfidenceScore score = analyzer.analyze(queries);
        
        assertTrue(score.getOverallScore() >= 0.0 && score.getOverallScore() <= 1.0);
        assertTrue(score.getStackTraceScore() >= 0.0 && score.getStackTraceScore() <= 1.0);
        assertTrue(score.getTimingScore() >= 0.0 && score.getTimingScore() <= 1.0);
        assertTrue(score.getPatternScore() >= 0.0 && score.getPatternScore() <= 1.0);
    }


    private List<QueryInfo> createQueriesWithLazyLoading(int count) {
        List<QueryInfo> queries = new ArrayList<>();
        StackTraceElement[] stack = createLazyLoadingStack();
        
        for (int i = 0; i < count; i++) {
            queries.add(new QueryInfo(
                "SELECT * FROM orders WHERE user_id = ?",
                "SELECT * FROM orders WHERE user_id = ?",
                5L,
                Instant.now(),
                stack,
                "thread-1",
                null
            ));
        }
        return queries;
    }

    private List<QueryInfo> createQueriesWithoutLazyLoading(int count) {
        List<QueryInfo> queries = new ArrayList<>();
        StackTraceElement[] stack = createNormalStack();
        
        for (int i = 0; i < count; i++) {
            queries.add(new QueryInfo(
                "SELECT * FROM orders WHERE user_id = ?",
                "SELECT * FROM orders WHERE user_id = ?",
                5L,
                Instant.now(),
                stack,
                "thread-1",
                null
            ));
        }
        return queries;
    }

    private List<QueryInfo> createQuickQueries(int count) {
        List<QueryInfo> queries = new ArrayList<>();
        StackTraceElement[] stack = createNormalStack();
        
        for (int i = 0; i < count; i++) {
            queries.add(new QueryInfo(
                "SELECT * FROM orders WHERE user_id = ?",
                "SELECT * FROM orders WHERE user_id = ?",
                2L,  // Very quick
                Instant.now(),
                stack,
                "thread-1",
                null
            ));
        }
        return queries;
    }

    private List<QueryInfo> createQueriesFromSameLocation(int count) {
        List<QueryInfo> queries = new ArrayList<>();
        StackTraceElement[] sameStack = new StackTraceElement[] {
            new StackTraceElement("com.example.UserService", "getOrders", "UserService.java", 42)
        };
        
        for (int i = 0; i < count; i++) {
            queries.add(new QueryInfo(
                "SELECT * FROM orders WHERE user_id = ?",
                "SELECT * FROM orders WHERE user_id = ?",
                5L,
                Instant.now(),
                sameStack,
                "thread-1",
                null
            ));
        }
        return queries;
    }

    private List<QueryInfo> createLowConfidenceQueries(int count) {
        List<QueryInfo> queries = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            StackTraceElement[] stack = new StackTraceElement[] {
                new StackTraceElement("com.example.Service" + i, "method", "Service.java", 10 + i)
            };
            
            queries.add(new QueryInfo(
                "SELECT * FROM table" + i,
                "SELECT * FROM table" + i,
                100L,  // Slow query
                Instant.now(),
                stack,
                "thread-1",
                null
            ));
        }
        return queries;
    }

    private StackTraceElement[] createLazyLoadingStack() {
        return new StackTraceElement[] {
            new StackTraceElement("org.hibernate.ByteBuddyInterceptor", "intercept", "ByteBuddyInterceptor.java", 100),
            new StackTraceElement("com.example.User", "getOrders", "User.java", 50),
            new StackTraceElement("com.example.UserService", "processUsers", "UserService.java", 42)
        };
    }

    private StackTraceElement[] createNormalStack() {
        return new StackTraceElement[] {
            new StackTraceElement("com.example.UserRepository", "findOrders", "UserRepository.java", 25),
            new StackTraceElement("com.example.UserService", "getOrders", "UserService.java", 42)
        };
    }
}
