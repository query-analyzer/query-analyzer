package io.queryanalyzer.core.detector;

import io.queryanalyzer.core.config.DetectorConfig;
import io.queryanalyzer.core.model.QueryInfo;
import io.queryanalyzer.core.model.QueryIssue;
import io.queryanalyzer.core.model.IssueType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


@DisplayName("NPlusOneDetectorEnhancements")
class NPlusOneDetectorEnhancedTest {
    

    @Nested
    @DisplayName("Backward Compatibility")
    class BackwardCompatibilityTests {
        
        @Test
        @DisplayName("default config should use CONFIDENCE mode (existing behavior)")
        void defaultConfigUsesConfidenceMode() {
            DetectorConfig config = DetectorConfig.defaults();
            assertEquals(DetectorConfig.DetectionMode.CONFIDENCE, config.getDetectionMode());
        }
        
        @Test
        @DisplayName("should detect N+1 with default config (regression test)")
        void detectNPlusOneWithDefaultConfig() {
            DetectorConfig config = DetectorConfig.defaults();
            NPlusOneDetector detector = new NPlusOneDetector(config);
            
            List<QueryInfo> queries = createNPlusOnePattern(
                "SELECT * FROM orders WHERE user_id = ?", 
                20
            );
            
            List<QueryIssue> issues = detector.detect(queries);
            
            assertFalse(issues.isEmpty(), "Should detect N+1 pattern");
            assertEquals(IssueType.N_PLUS_ONE, issues.get(0).getType());
        }
        
        @Test
        @DisplayName("should not detect legitimate batch queries (regression test)")
        void notDetectBatchQueries() {
            DetectorConfig config = DetectorConfig.defaults();
            NPlusOneDetector detector = new NPlusOneDetector(config);
            
            List<QueryInfo> queries = createBatchPattern(
                "SELECT * FROM orders WHERE user_id IN (?, ?, ?, ?)", 
                5
            );
            
            List<QueryIssue> issues = detector.detect(queries);
            
            assertTrue(issues.isEmpty(), "Should not flag batch queries as N+1");
        }
        
        @Test
        @DisplayName("should respect minRepetitions config (regression test)")
        void respectMinRepetitions() {
            DetectorConfig config = DetectorConfig.builder()
                .minRepetitions(5)
                .build();
            NPlusOneDetector detector = new NPlusOneDetector(config);
            
            // Only 4 queries - below threshold
            List<QueryInfo> queries = createNPlusOnePattern(
                "SELECT * FROM orders WHERE user_id = ?", 
                4
            );
            
            List<QueryIssue> issues = detector.detect(queries);
            
            assertTrue(issues.isEmpty(), "Should not detect when below minRepetitions");
        }
    }
    
    // ========== Detection Mode Tests ==========
    
    @Nested
    @DisplayName("Detection Modes")
    class DetectionModeTests {
        
        @Test
        @DisplayName("THRESHOLD mode should detect based on count only")
        void thresholdModeDetectsOnCount() {
            DetectorConfig config = DetectorConfig.builder()
                .detectionMode(DetectorConfig.DetectionMode.THRESHOLD)
                .minRepetitions(3)
                .build();
            NPlusOneDetector detector = new NPlusOneDetector(config);
            
            List<QueryInfo> queries = createNPlusOnePattern(
                "SELECT * FROM orders WHERE user_id = ?", 
                5
            );
            
            List<QueryIssue> issues = detector.detect(queries);
            
            assertFalse(issues.isEmpty(), "THRESHOLD mode should detect repeated queries");
        }
        
        @Test
        @DisplayName("THRESHOLD mode should skip below threshold")
        void thresholdModeSkipsBelowThreshold() {
            DetectorConfig config = DetectorConfig.builder()
                .detectionMode(DetectorConfig.DetectionMode.THRESHOLD)
                .minRepetitions(10)
                .build();
            NPlusOneDetector detector = new NPlusOneDetector(config);
            
            // Only 5 queries - below threshold of 10
            List<QueryInfo> queries = createNPlusOnePattern(
                "SELECT * FROM orders WHERE user_id = ?", 
                5
            );
            
            List<QueryIssue> issues = detector.detect(queries);
            
            assertTrue(issues.isEmpty(), "Should not detect when below threshold");
        }
        
        @Test
        @DisplayName("CONFIDENCE mode should use confidence scoring")
        void confidenceModeUsesScoring() {
            DetectorConfig config = DetectorConfig.builder()
                .detectionMode(DetectorConfig.DetectionMode.CONFIDENCE)
                .minConfidenceThreshold(0.3) // Low threshold for test
                .build();
            NPlusOneDetector detector = new NPlusOneDetector(config);
            
            List<QueryInfo> queries = createNPlusOnePattern(
                "SELECT * FROM orders WHERE user_id = ?", 
                20
            );
            
            List<QueryIssue> issues = detector.detect(queries);
            
            // Should detect - queries are from same pattern
            assertFalse(issues.isEmpty(), "CONFIDENCE mode should detect N+1 pattern");
        }
        
        @Test
        @DisplayName("HYBRID mode requires both threshold AND confidence")
        void hybridModeRequiresBoth() {
            DetectorConfig config = DetectorConfig.builder()
                .detectionMode(DetectorConfig.DetectionMode.HYBRID)
                .minRepetitions(3)
                .minConfidenceThreshold(0.3)
                .build();
            NPlusOneDetector detector = new NPlusOneDetector(config);
            
            List<QueryInfo> queries = createNPlusOnePattern(
                "SELECT * FROM orders WHERE user_id = ?", 
                20
            );
            
            List<QueryIssue> issues = detector.detect(queries);
            
            // Should detect - meets both criteria
            assertFalse(issues.isEmpty(), "HYBRID mode should detect when both criteria met");
        }
    }
    
    @Nested
    @DisplayName("Enhanced Suggestions")
    class EnhancedSuggestionsTests {
        
        @Test
        @DisplayName("should include relationship inference when enabled")
        void includeRelationshipInference() {
            DetectorConfig config = DetectorConfig.builder()
                .enableRelationshipInference(true)
                .build();
            NPlusOneDetector detector = new NPlusOneDetector(config);
            
            List<QueryInfo> queries = createNPlusOnePattern(
                "SELECT * FROM orders WHERE user_id = ?", 
                20
            );
            
            List<QueryIssue> issues = detector.detect(queries);
            
            assertFalse(issues.isEmpty());
            
            String allSuggestions = String.join("\n", issues.get(0).getSuggestions());
            // Should contain relationship info
            assertTrue(
                allSuggestions.toLowerCase().contains("relationship") ||
                allSuggestions.toLowerCase().contains("user") ||
                allSuggestions.toLowerCase().contains("order"),
                "Should include relationship inference in suggestions"
            );
        }
        
        @Test
        @DisplayName("should skip relationship inference when disabled")
        void skipRelationshipInferenceWhenDisabled() {
            DetectorConfig config = DetectorConfig.builder()
                .enableRelationshipInference(false)
                .enableFrameworkSuggestions(false)
                .build();
            NPlusOneDetector detector = new NPlusOneDetector(config);
            
            List<QueryInfo> queries = createNPlusOnePattern(
                "SELECT * FROM orders WHERE user_id = ?", 
                20
            );
            
            List<QueryIssue> issues = detector.detect(queries);
            
            assertFalse(issues.isEmpty());
            assertFalse(issues.get(0).getSuggestions().isEmpty());
        }
        
        @Test
        @DisplayName("should detect Hibernate framework from stack trace")
        void detectHibernateFramework() {
            DetectorConfig config = DetectorConfig.builder()
                .enableFrameworkSuggestions(true)
                .build();
            NPlusOneDetector detector = new NPlusOneDetector(config);
            
            List<QueryInfo> queries = createNPlusOnePatternWithHibernateStack(
                "SELECT * FROM orders WHERE user_id = ?", 
                20
            );
            
            List<QueryIssue> issues = detector.detect(queries);
            
            assertFalse(issues.isEmpty());
            
            String allSuggestions = String.join("\n", issues.get(0).getSuggestions());
            assertTrue(
                allSuggestions.contains("Hibernate") || 
                allSuggestions.contains("JPA") ||
                allSuggestions.contains("JOIN FETCH") ||
                allSuggestions.contains("BatchSize"),
                "Should include Hibernate-specific suggestions"
            );
        }
    }
    

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {
        
        @Test
        @DisplayName("should handle empty query list")
        void handleEmptyQueryList() {
            DetectorConfig config = DetectorConfig.defaults();
            NPlusOneDetector detector = new NPlusOneDetector(config);
            
            List<QueryIssue> issues = detector.detect(new ArrayList<>());
            
            assertTrue(issues.isEmpty());
        }
        
        @Test
        @DisplayName("should handle null query list")
        void handleNullQueryList() {
            DetectorConfig config = DetectorConfig.defaults();
            NPlusOneDetector detector = new NPlusOneDetector(config);
            
            List<QueryIssue> issues = detector.detect(null);
            
            assertTrue(issues.isEmpty());
        }
        
        @Test
        @DisplayName("should handle queries without stack traces")
        void handleQueriesWithoutStackTraces() {
            DetectorConfig config = DetectorConfig.builder()
                .detectionMode(DetectorConfig.DetectionMode.THRESHOLD)
                .minRepetitions(3)
                .enableFrameworkSuggestions(true)
                .build();
            NPlusOneDetector detector = new NPlusOneDetector(config);
            
            List<QueryInfo> queries = createNPlusOnePatternWithoutStackTrace(
                "SELECT * FROM orders WHERE user_id = ?", 
                20
            );
            
            List<QueryIssue> issues = detector.detect(queries);
            
            assertFalse(issues.isEmpty(), "Should still detect N+1 without stack traces using THRESHOLD mode");
        }
        
        @Test
        @DisplayName("should handle malformed SQL gracefully")
        void handleMalformedSql() {
            DetectorConfig config = DetectorConfig.defaults();
            NPlusOneDetector detector = new NPlusOneDetector(config);
            
            List<QueryInfo> queries = createNPlusOnePattern(
                "SELEC FORM orders WHER user_id = ?", // Malformed
                20
            );
            
            assertDoesNotThrow(() -> detector.detect(queries));
        }
    }
    

    private List<QueryInfo> createNPlusOnePattern(String sql, int count) {
        List<QueryInfo> queries = new ArrayList<>();
        Instant baseTime = Instant.now();
        
        for (int i = 0; i < count; i++) {
            queries.add(QueryInfo.builder()
                .sql(sql.replace("?", String.valueOf(i)))
                .normalizedSql(sql.toLowerCase())
                .executionTimeMs(5)
                .timestamp(baseTime.plusMillis(i * 10))
                .stackTrace(createSimpleStackTrace())
                .build());
        }
        
        return queries;
    }
    
    private List<QueryInfo> createBatchPattern(String sql, int count) {
        List<QueryInfo> queries = new ArrayList<>();
        Instant baseTime = Instant.now();
        
        for (int i = 0; i < count; i++) {
            queries.add(QueryInfo.builder()
                .sql(sql)
                .normalizedSql(sql.toLowerCase())
                .executionTimeMs(20)
                .timestamp(baseTime.plusMillis(i * 100))
                .stackTrace(createSimpleStackTrace())
                .build());
        }
        
        return queries;
    }
    
    private List<QueryInfo> createNPlusOnePatternWithHibernateStack(String sql, int count) {
        List<QueryInfo> queries = new ArrayList<>();
        Instant baseTime = Instant.now();
        
        for (int i = 0; i < count; i++) {
            queries.add(QueryInfo.builder()
                .sql(sql.replace("?", String.valueOf(i)))
                .normalizedSql(sql.toLowerCase())
                .executionTimeMs(5)
                .timestamp(baseTime.plusMillis(i * 10))
                .stackTrace(createHibernateStackTrace())
                .build());
        }
        
        return queries;
    }
    
    private List<QueryInfo> createNPlusOnePatternWithoutStackTrace(String sql, int count) {
        List<QueryInfo> queries = new ArrayList<>();
        Instant baseTime = Instant.now();
        
        for (int i = 0; i < count; i++) {
            queries.add(QueryInfo.builder()
                .sql(sql.replace("?", String.valueOf(i)))
                .normalizedSql(sql.toLowerCase())
                .executionTimeMs(5)
                .timestamp(baseTime.plusMillis(i * 10))
                .stackTrace(null) // No stack trace
                .build());
        }
        
        return queries;
    }
    
    private StackTraceElement[] createSimpleStackTrace() {
        return new StackTraceElement[] {
            new StackTraceElement("com.example.UserService", "findUsers", "UserService.java", 50),
            new StackTraceElement("com.example.UserController", "getUsers", "UserController.java", 30)
        };
    }
    
    private StackTraceElement[] createHibernateStackTrace() {
        return new StackTraceElement[] {
            new StackTraceElement("com.example.UserService", "findUsers", "UserService.java", 50),
            new StackTraceElement("org.hibernate.collection.internal.AbstractPersistentCollection", 
                "initialize", "AbstractPersistentCollection.java", 100),
            new StackTraceElement("org.hibernate.internal.SessionImpl", 
                "initializeCollection", "SessionImpl.java", 200)
        };
    }
}
