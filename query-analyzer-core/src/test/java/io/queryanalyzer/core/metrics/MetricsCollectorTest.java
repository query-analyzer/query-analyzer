package io.queryanalyzer.core.metrics;

import io.queryanalyzer.core.model.IssueType;
import io.queryanalyzer.core.model.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MetricsCollectorTest {

    private MetricsCollector collector;

    @BeforeEach
    void setUp() {
        collector = new MetricsCollector();
    }

    @Test
    void testRecordIssue() {
        collector.recordIssue(IssueType.N_PLUS_ONE, Severity.WARNING);
        
        assertEquals(1, collector.getTotalIssuesDetected());
        assertEquals(1, collector.getIssuesByType(IssueType.N_PLUS_ONE));
        assertEquals(1, collector.getIssuesBySeverity(Severity.WARNING));
    }

    @Test
    void testRecordMultipleIssues() {
        collector.recordIssue(IssueType.N_PLUS_ONE, Severity.WARNING);
        collector.recordIssue(IssueType.N_PLUS_ONE, Severity.ERROR);
        collector.recordIssue(IssueType.SLOW_QUERY, Severity.CRITICAL);
        
        assertEquals(3, collector.getTotalIssuesDetected());
        assertEquals(2, collector.getIssuesByType(IssueType.N_PLUS_ONE));
        assertEquals(1, collector.getIssuesByType(IssueType.SLOW_QUERY));
        assertEquals(1, collector.getIssuesBySeverity(Severity.WARNING));
        assertEquals(1, collector.getIssuesBySeverity(Severity.ERROR));
        assertEquals(1, collector.getIssuesBySeverity(Severity.CRITICAL));
    }

    @Test
    void testRecordRequestAnalyzed() {
        collector.recordRequestAnalyzed(25, 5L);
        
        assertEquals(1, collector.getTotalRequestsAnalyzed());
        assertEquals(25, collector.getCurrentQueriesInRequest());
        assertEquals(5L, collector.getLastDetectionDurationMs());
    }

    @Test
    void testQueryCountBuckets() {
        collector.recordRequestAnalyzed(5, 1L);
        collector.recordRequestAnalyzed(30, 2L);
        collector.recordRequestAnalyzed(75, 3L);
        collector.recordRequestAnalyzed(200, 4L);
        collector.recordRequestAnalyzed(600, 5L);
        
        assertEquals(1, collector.getQueryCountBucket("0-10"));
        assertEquals(1, collector.getQueryCountBucket("11-50"));
        assertEquals(1, collector.getQueryCountBucket("51-100"));
        assertEquals(1, collector.getQueryCountBucket("101-500"));
        assertEquals(1, collector.getQueryCountBucket("500+"));
    }

    @Test
    void testDetectionDurationBuckets() {
        collector.recordRequestAnalyzed(10, 1L);
        collector.recordRequestAnalyzed(10, 3L);
        collector.recordRequestAnalyzed(10, 7L);
        collector.recordRequestAnalyzed(10, 25L);
        collector.recordRequestAnalyzed(10, 100L);
        
        assertEquals(1, collector.getDetectionDurationBucket("0-1"));
        assertEquals(1, collector.getDetectionDurationBucket("1-5"));
        assertEquals(1, collector.getDetectionDurationBucket("5-10"));
        assertEquals(1, collector.getDetectionDurationBucket("10-50"));
        assertEquals(1, collector.getDetectionDurationBucket("50+"));
    }

    @Test
    void testGetSnapshot() {
        collector.recordIssue(IssueType.N_PLUS_ONE, Severity.WARNING);
        collector.recordIssue(IssueType.SLOW_QUERY, Severity.ERROR);
        collector.recordRequestAnalyzed(50, 10L);
        
        MetricsCollector.MetricsSnapshot snapshot = collector.getSnapshot();
        
        assertNotNull(snapshot);
        assertEquals(2, snapshot.getTotalIssues());
        assertEquals(1, snapshot.getTotalRequests());
        assertEquals(50, snapshot.getCurrentQueries());
        assertEquals(10L, snapshot.getLastDurationMs());
        assertEquals(1, snapshot.getNPlusOneCount());
        assertEquals(1, snapshot.getSlowQueryCount());
        assertEquals(1, snapshot.getWarningCount());
        assertEquals(1, snapshot.getErrorCount());
    }

    @Test
    void testReset() {
        collector.recordIssue(IssueType.N_PLUS_ONE, Severity.WARNING);
        collector.recordRequestAnalyzed(50, 10L);
        
        collector.reset();
        
        assertEquals(0, collector.getTotalIssuesDetected());
        assertEquals(0, collector.getTotalRequestsAnalyzed());
        assertEquals(0, collector.getCurrentQueriesInRequest());
        assertEquals(0, collector.getLastDetectionDurationMs());
    }

    @Test
    void testThreadSafety() throws InterruptedException {
        Thread[] threads = new Thread[10];
        
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 100; j++) {
                    collector.recordIssue(IssueType.N_PLUS_ONE, Severity.WARNING);
                    collector.recordRequestAnalyzed(10, 1L);
                }
            });
        }
        
        for (Thread thread : threads) {
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        
        assertEquals(1000, collector.getTotalIssuesDetected());
        assertEquals(1000, collector.getTotalRequestsAnalyzed());
    }

    @Test
    void testGetNonExistentMetrics() {
        long result = collector.getIssuesByType(IssueType.SLOW_QUERY);
        
        assertEquals(0, result);
    }
}
