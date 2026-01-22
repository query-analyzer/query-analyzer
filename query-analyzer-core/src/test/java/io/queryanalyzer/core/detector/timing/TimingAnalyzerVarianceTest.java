package io.queryanalyzer.core.detector.timing;
import io.queryanalyzer.core.config.TestConfigFactory;

import io.queryanalyzer.core.model.QueryInfo;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TimingAnalyzerVarianceTest {

    private final TimingAnalyzer analyzer = TestConfigFactory.createTimingAnalyzer();

    @Test
    void shouldDetectConsistentDeliberatePacing() {
        List<QueryInfo> queries = new ArrayList<>();
        Instant start = Instant.now();
        
        for (int i = 0; i < 10; i++) {
            queries.add(createQuery(
                start.plusMillis(i * 150),
                "SELECT * FROM users WHERE id = ?",
                50
            ));
        }
        
        boolean result = analyzer.hasDeliberatePacing(queries);
        
        assertThat(result).isTrue();
    }

    @Test
    void shouldRejectInconsistentPacing() {
        List<QueryInfo> queries = new ArrayList<>();
        Instant start = Instant.now();
        
        for (int i = 0; i < 5; i++) {
            queries.add(createQuery(
                start.plusMillis(i * 51),
                "SELECT * FROM users WHERE id = ?",
                50
            ));
        }
        
        queries.add(createQuery(
            start.plusMillis(5 * 51 + 200),
            "SELECT * FROM users WHERE id = ?",
            50
        ));
        
        boolean result = analyzer.hasDeliberatePacing(queries);
        
        assertThat(result).isFalse();
    }

    @Test
    void shouldHandleSmallSampleGracefully() {
        List<QueryInfo> queries = new ArrayList<>();
        Instant start = Instant.now();
        
        for (int i = 0; i < 3; i++) {
            queries.add(createQuery(
                start.plusMillis(i * 110),
                "SELECT * FROM users WHERE id = ?",
                50
            ));
        }
        
        boolean result = analyzer.hasDeliberatePacing(queries);
        
        assertThat(result).isTrue();
    }

    @Test
    void shouldHandleOutliersWithVarianceCheck() {
        List<QueryInfo> queries = new ArrayList<>();
        Instant start = Instant.now();
        
        for (int i = 0; i < 9; i++) {
            queries.add(createQuery(
                start.plusMillis(i * 150),
                "SELECT * FROM users WHERE id = ?",
                50
            ));
        }
        
        // 1 outlier with smaller variance
        queries.add(createQuery(
            start.plusMillis(9 * 150 + 30),
            "SELECT * FROM users WHERE id = ?",
            50
        ));
        
        boolean result = analyzer.hasDeliberatePacing(queries);
        
        assertThat(result).isTrue();
    }

    @Test
    void shouldRejectTightLoop() {
        List<QueryInfo> queries = new ArrayList<>();
        Instant start = Instant.now();
        
        for (int i = 0; i < 10; i++) {
            queries.add(createQuery(
                start.plusMillis(i * 52),
                "SELECT * FROM users WHERE id = ?",
                50
            ));
        }
        
        boolean result = analyzer.hasDeliberatePacing(queries);
        
        assertThat(result).isFalse();
    }

    @Test
    void shouldHandleOverlappingQueries() {
        List<QueryInfo> queries = new ArrayList<>();
        Instant start = Instant.now();
        
        for (int i = 0; i < 5; i++) {
            queries.add(createQuery(
                start,
                "SELECT * FROM users WHERE id = ?",
                50
            ));
        }
        
        boolean result = analyzer.hasDeliberatePacing(queries);
        
        assertThat(result).isFalse();
    }

    @Test
    void shouldHandleEmptyGaps() {
        List<QueryInfo> queries = new ArrayList<>();
        queries.add(createQuery(
            Instant.now(),
            "SELECT * FROM users WHERE id = ?",
            50
        ));
        
        boolean result = analyzer.hasDeliberatePacing(queries);
        
        assertThat(result).isFalse();
    }

    private QueryInfo createQuery(Instant timestamp, String sql, long executionTimeMs) {
        return new QueryInfo(
            sql,
            sql,
            executionTimeMs,
            timestamp,
            null,
            "main",
            null
        );
    }
}
