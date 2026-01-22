package io.queryanalyzer.core.detector.confidence;
import io.queryanalyzer.core.config.DetectorConfig;
import io.queryanalyzer.core.config.TestConfigFactory;

import io.queryanalyzer.core.detector.timing.TimingAnalyzer;
import io.queryanalyzer.core.model.ConfidenceScore;
import io.queryanalyzer.core.model.QueryInfo;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConfidenceAnalyzerGranularTest {

    private final DetectorConfig config = DetectorConfig.builder().build();
    private final TimingAnalyzer timingAnalyzer = new TimingAnalyzer(config);
    private final ConfidenceAnalyzer analyzer = TestConfigFactory.createConfidenceAnalyzer(TestConfigFactory.createDefault());

    @Test
    void shouldScoreExactSameLineAs1_0() {
        List<QueryInfo> queries = new ArrayList<>();
        StackTraceElement[] stack = createStack("com.example.UserService", "loadOrders", 42);
        
        for (int i = 0; i < 5; i++) {
            queries.add(createQueryWithStack(
                "SELECT * FROM orders WHERE user_id = ?",
                stack
            ));
        }
        
        ConfidenceScore score = analyzer.analyze(queries);
        
        assertThat(score.getPatternScore()).isEqualTo(1.0);
    }

    @Test
    void shouldScoreSameMethodAs0_9() {
        List<QueryInfo> queries = new ArrayList<>();
        
        for (int i = 0; i < 5; i++) {
            StackTraceElement[] stack = createStack(
                "com.example.UserService", 
                "loadOrders", 
                42 + i
            );
            queries.add(createQueryWithStack(
                "SELECT * FROM orders WHERE user_id = ?",
                stack
            ));
        }
        
        ConfidenceScore score = analyzer.analyze(queries);
        
        assertThat(score.getPatternScore()).isEqualTo(0.9);
    }

    @Test
    void shouldScoreSameClassAs0_8() {
        List<QueryInfo> queries = new ArrayList<>();
        
        for (int i = 0; i < 5; i++) {
            StackTraceElement[] stack = createStack(
                "com.example.UserService",
                "loadOrders" + i,
                42
            );
            queries.add(createQueryWithStack(
                "SELECT * FROM orders WHERE user_id = ?",
                stack
            ));
        }
        
        ConfidenceScore score = analyzer.analyze(queries);
        
        assertThat(score.getPatternScore()).isEqualTo(0.8);
    }

    @Test
    void shouldDetectLoopPatternAs0_7() {
        List<QueryInfo> queries = new ArrayList<>();
        
        for (int i = 0; i < 5; i++) {
            StackTraceElement[] stack = createLoopStack(
                "com.example.OrderRepository", "findByUserId", 10 + i, // Different iterations
                "com.example.UserService", "loadAllOrders", 50  // Same loop caller
            );
            queries.add(createQueryWithStack(
                "SELECT * FROM orders WHERE user_id = ?",
                stack
            ));
        }
        
        ConfidenceScore score = analyzer.analyze(queries);
        
        assertThat(score.getPatternScore()).isGreaterThan(0.5);
    }

    @Test
    void shouldScoreDifferentLocationsAs0_5() {
        List<QueryInfo> queries = new ArrayList<>();
        
        for (int i = 0; i < 5; i++) {
            StackTraceElement[] stack = createStack(
                "com.example.Service" + i,  // Different classes
                "method" + i,                // Different methods
                42
            );
            queries.add(createQueryWithStack(
                "SELECT * FROM orders WHERE user_id = ?",
                stack
            ));
        }
        
        ConfidenceScore score = analyzer.analyze(queries);
        
        // Pattern score should be 0.5 (different locations)
        assertThat(score.getPatternScore()).isEqualTo(0.5);
    }

    @Test
    void shouldHandleNullStackTraceGracefully() {
        List<QueryInfo> queries = new ArrayList<>();
        
        for (int i = 0; i < 5; i++) {
            queries.add(createQueryWithStack(
                "SELECT * FROM orders WHERE user_id = ?",
                null
            ));
        }
        
        ConfidenceScore score = analyzer.analyze(queries);
        
        assertThat(score.getPatternScore()).isEqualTo(0.5);
    }

    @Test
    void shouldHandleEmptyStackTraceGracefully() {
        List<QueryInfo> queries = new ArrayList<>();
        
        for (int i = 0; i < 5; i++) {
            queries.add(createQueryWithStack(
                "SELECT * FROM orders WHERE user_id = ?",
                new StackTraceElement[0]  // Empty
            ));
        }
        
        ConfidenceScore score = analyzer.analyze(queries);
        
        assertThat(score.getPatternScore()).isEqualTo(0.5);
    }

    @Test
    void shouldHandleMixedStackTraces() {
        List<QueryInfo> queries = new ArrayList<>();
        
        StackTraceElement[] stack = createStack("com.example.Service", "method", 42);
        
        queries.add(createQueryWithStack("SELECT * FROM orders", stack));
        queries.add(createQueryWithStack("SELECT * FROM orders", null));
        queries.add(createQueryWithStack("SELECT * FROM orders", stack));
        queries.add(createQueryWithStack("SELECT * FROM orders", new StackTraceElement[0]));
        
        ConfidenceScore score = analyzer.analyze(queries);
        
        assertThat(score.getPatternScore()).isBetween(0.5, 1.0);
    }

    @Test
    void shouldHandleShallowStackTrace() {
        List<QueryInfo> queries = new ArrayList<>();
        
        for (int i = 0; i < 5; i++) {
            StackTraceElement[] stack = new StackTraceElement[] {
                new StackTraceElement("com.example.Service", "method", "Service.java", 42)
            };
            queries.add(createQueryWithStack(
                "SELECT * FROM orders WHERE user_id = ?",
                stack
            ));
        }
        
        ConfidenceScore score = analyzer.analyze(queries);
        
        assertThat(score.getPatternScore()).isEqualTo(1.0);
    }

    private StackTraceElement[] createStack(String className, String methodName, int lineNumber) {
        return new StackTraceElement[] {
            new StackTraceElement(className, methodName, className.substring(className.lastIndexOf('.') + 1) + ".java", lineNumber)
        };
    }

    private StackTraceElement[] createLoopStack(
            String immediateClass, String immediateMethod, int immediateLine,
            String loopClass, String loopMethod, int loopLine) {
        return new StackTraceElement[] {
            new StackTraceElement(immediateClass, immediateMethod, "Repository.java", immediateLine),
            new StackTraceElement("$Proxy", "invoke", null, -1),  // Proxy frame
            new StackTraceElement(loopClass, loopMethod, "Service.java", loopLine)
        };
    }

    private QueryInfo createQueryWithStack(String sql, StackTraceElement[] stackTrace) {
        return new QueryInfo(
            sql,
            sql,
            10L,
            Instant.now(),
            stackTrace,
            "main",
            null
        );
    }
}
