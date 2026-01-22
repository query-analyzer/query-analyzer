package io.queryanalyzer.core.detector.timing;
import io.queryanalyzer.core.config.DetectorConfig;
import io.queryanalyzer.core.config.TestConfigFactory;

import io.queryanalyzer.core.model.QueryInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TimingAnalyzerTest {

    private TimingAnalyzer analyzer;
    private DetectorConfig config;

    @BeforeEach
    void setUp() {
        config = TestConfigFactory.createDefault();
        analyzer = TestConfigFactory.createTimingAnalyzer();
    }

    @Test
    void testTightLoopDetection() {
        List<QueryInfo> queries = createQueriesWithTotalTime(500L, 10);
        
        TimingPattern pattern = analyzer.analyzePattern(queries);
        
        assertEquals(TimingPattern.TIGHT_LOOP, pattern);
        assertTrue(pattern.isSuspicious());
    }

    @Test
    void testModerateLoopDetection() {
        List<QueryInfo> queries = createQueriesWithTotalTime(2000L, 10);
        
        TimingPattern pattern = analyzer.analyzePattern(queries);
        
        assertEquals(TimingPattern.MODERATE_LOOP, pattern);
        assertTrue(pattern.isSuspicious());
    }

    @Test
    void testSlowLoopDetection() {
        List<QueryInfo> queries = createQueriesWithTotalTime(5000L, 10);
        
        TimingPattern pattern = analyzer.analyzePattern(queries);
        
        assertEquals(TimingPattern.SLOW_LOOP, pattern);
        assertFalse(pattern.isSuspicious());
    }

    @Test
    void testDeliberatePacingDetection() {
        List<QueryInfo> queries = createPacedQueries(5, 100L);
        
        TimingPattern pattern = analyzer.analyzePattern(queries);
        
        assertEquals(TimingPattern.DELIBERATE_PACING, pattern);
        assertFalse(pattern.isSuspicious());
    }

    @Test
    void testHasDeliberatePacing() {
        List<QueryInfo> queries = createPacedQueries(5, 60L);
        
        boolean hasPacing = analyzer.hasDeliberatePacing(queries);
        
        assertTrue(hasPacing, "Should detect pacing with 60ms gaps");
    }

    @Test
    void testNoDeliberatePacingWithSmallGaps() {
        List<QueryInfo> queries = createPacedQueries(5, 5L);
        
        boolean hasPacing = analyzer.hasDeliberatePacing(queries);
        
        assertFalse(hasPacing, "Small gaps should not be considered pacing");
    }

    @Test
    void testCalculateTotalTime() {
        List<QueryInfo> queries = new ArrayList<>();
        queries.add(createQuery("SELECT 1", 10L));
        queries.add(createQuery("SELECT 2", 20L));
        queries.add(createQuery("SELECT 3", 30L));
        
        long totalTime = analyzer.calculateTotalTime(queries);
        
        assertEquals(60L, totalTime);
    }

    @Test
    void testUnknownPatternForEmptyQueries() {
        List<QueryInfo> queries = new ArrayList<>();
        
        TimingPattern pattern = analyzer.analyzePattern(queries);
        
        assertEquals(TimingPattern.UNKNOWN, pattern);
    }

    @Test
    void testUnknownPatternForSingleQuery() {
        List<QueryInfo> queries = new ArrayList<>();
        queries.add(createQuery("SELECT 1", 10L));
        
        TimingPattern pattern = analyzer.analyzePattern(queries);
        
        assertEquals(TimingPattern.UNKNOWN, pattern);
    }

    @Test
    void testVerySlowQueriesPattern() {
        List<QueryInfo> queries = createQueriesWithTotalTime(30000L, 10);
        
        TimingPattern pattern = analyzer.analyzePattern(queries);
        
        assertEquals(TimingPattern.UNKNOWN, pattern);
    }

    @Test
    void testNoPacingWithInconsistentGaps() {
        List<QueryInfo> queries = new ArrayList<>();
        Instant now = Instant.now();
        
        queries.add(createQueryAt("SELECT 1", 10L, now));
        queries.add(createQueryAt("SELECT 2", 10L, now.plusMillis(100))); // 90ms gap
        queries.add(createQueryAt("SELECT 3", 10L, now.plusMillis(120))); // 10ms gap
        queries.add(createQueryAt("SELECT 4", 10L, now.plusMillis(220))); // 90ms gap
        queries.add(createQueryAt("SELECT 5", 10L, now.plusMillis(240))); // 10ms gap
        queries.add(createQueryAt("SELECT 6", 10L, now.plusMillis(340))); // 90ms gap
        queries.add(createQueryAt("SELECT 7", 10L, now.plusMillis(360))); // 10ms gap
        
        boolean hasPacing = analyzer.hasDeliberatePacing(queries);
        
        assertFalse(hasPacing, "Inconsistent gaps should not be detected as pacing");
    }

    @Test
    void testPacingWithConsistentGaps() {
        List<QueryInfo> queries = new ArrayList<>();
        Instant now = Instant.now();
        
        for (int i = 0; i < 5; i++) {
            queries.add(createQueryAt("SELECT " + i, 10L, now.plusMillis(i * 110L))); // 100ms gaps consistently
        }
        
        boolean hasPacing = analyzer.hasDeliberatePacing(queries);
        
        assertTrue(hasPacing, "Consistent gaps should be detected as pacing");
    }

    @Test
    void testTimingPatternFromTotalTime() {
        DetectorConfig config = DetectorConfig.builder()
            .tightLoopThresholdMs(1000)
            .moderateLoopThresholdMs(3000)
            .slowLoopThresholdMs(10000)
            .build();
        
        TimingPattern tight = TimingPattern.fromTotalTime(500, config);
        TimingPattern moderate = TimingPattern.fromTotalTime(2000, config);
        TimingPattern slow = TimingPattern.fromTotalTime(5000, config);
        TimingPattern unknown = TimingPattern.fromTotalTime(15000, config);
        
        assertEquals(TimingPattern.TIGHT_LOOP, tight);
        assertEquals(TimingPattern.MODERATE_LOOP, moderate);
        assertEquals(TimingPattern.SLOW_LOOP, slow);
        assertEquals(TimingPattern.UNKNOWN, unknown);
    }


    private List<QueryInfo> createQueriesWithTotalTime(long totalTimeMs, int count) {
        List<QueryInfo> queries = new ArrayList<>();
        long timePerQuery = totalTimeMs / count;
        Instant now = Instant.now();
        
        for (int i = 0; i < count; i++) {
            queries.add(createQueryAt(
                "SELECT * FROM table WHERE id = " + i,
                timePerQuery,
                now.plusMillis(i * (timePerQuery + 1))
            ));
        }
        
        return queries;
    }

    private List<QueryInfo> createPacedQueries(int count, long gapMs) {
        List<QueryInfo> queries = new ArrayList<>();
        Instant now = Instant.now();
        long queryTime = 10L;
        
        for (int i = 0; i < count; i++) {
            Instant timestamp = now.plusMillis(i * (queryTime + gapMs));
            queries.add(createQueryAt(
                "SELECT * FROM table WHERE id = " + i,
                queryTime,
                timestamp
            ));
        }
        
        return queries;
    }

    private QueryInfo createQuery(String sql, long executionTimeMs) {
        return createQueryAt(sql, executionTimeMs, Instant.now());
    }

    private QueryInfo createQueryAt(String sql, long executionTimeMs, Instant timestamp) {
        return new QueryInfo(
            sql,
            sql,
            executionTimeMs,
            timestamp,
            null,
            "test-thread",
            null
        );
    }
}
