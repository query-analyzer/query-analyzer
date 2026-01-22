package io.queryanalyzer.core.metrics;

import io.queryanalyzer.core.model.IssueType;
import io.queryanalyzer.core.model.Severity;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;


public class MetricsCollector {

    private final ConcurrentHashMap<IssueType, LongAdder> issuesByType = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Severity, LongAdder> issuesBySeverity = new ConcurrentHashMap<>();
    private final LongAdder totalIssuesDetected = new LongAdder();
    private final LongAdder totalRequestsAnalyzed = new LongAdder();
    
    private final AtomicLong currentQueriesInRequest = new AtomicLong(0);
    private final AtomicLong lastDetectionDurationMs = new AtomicLong(0);
    
    private final ConcurrentHashMap<String, LongAdder> queriesPerRequestBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> detectionDurationBuckets = new ConcurrentHashMap<>();
    
    private final Object resetLock = new Object();
    
    private final java.util.concurrent.atomic.AtomicBoolean resetting = new AtomicBoolean(false);

    public MetricsCollector() {
        initializeBuckets();
    }

    private void initializeBuckets() {
        queriesPerRequestBuckets.put("0-10", new LongAdder());
        queriesPerRequestBuckets.put("11-50", new LongAdder());
        queriesPerRequestBuckets.put("51-100", new LongAdder());
        queriesPerRequestBuckets.put("101-500", new LongAdder());
        queriesPerRequestBuckets.put("500+", new LongAdder());
        
        detectionDurationBuckets.put("0-1", new LongAdder());
        detectionDurationBuckets.put("1-5", new LongAdder());
        detectionDurationBuckets.put("5-10", new LongAdder());
        detectionDurationBuckets.put("10-50", new LongAdder());
        detectionDurationBuckets.put("50+", new LongAdder());
    }


    public void recordIssue(IssueType type, Severity severity) {
        if (resetting.get()) {
            return;
        }
        
        totalIssuesDetected.increment();
        issuesByType.computeIfAbsent(type, k -> new LongAdder()).increment();
        issuesBySeverity.computeIfAbsent(severity, k -> new LongAdder()).increment();
    }


    public void recordRequestAnalyzed(int queryCount, long durationMs) {
        if (resetting.get()) {
            return;
        }
        
        totalRequestsAnalyzed.increment();
        currentQueriesInRequest.set(queryCount);
        lastDetectionDurationMs.set(durationMs);
        
        recordQueryCountBucket(queryCount);
        recordDurationBucket(durationMs);
    }

    private void recordQueryCountBucket(int count) {
        String bucket = getQueryCountBucketName(count);
        LongAdder adder = queriesPerRequestBuckets.get(bucket);
        if (adder != null) {
            adder.increment();
        }
    }
    
    private String getQueryCountBucketName(int count) {
        if (count <= 10) return "0-10";
        if (count <= 50) return "11-50";
        if (count <= 100) return "51-100";
        if (count <= 500) return "101-500";
        return "500+";
    }

    private void recordDurationBucket(long durationMs) {
        String bucket = getDurationBucketName(durationMs);
        LongAdder adder = detectionDurationBuckets.get(bucket);
        if (adder != null) {
            adder.increment();
        }
    }
    
    private String getDurationBucketName(long durationMs) {
        if (durationMs <= 1) return "0-1";
        if (durationMs <= 5) return "1-5";
        if (durationMs <= 10) return "5-10";
        if (durationMs <= 50) return "10-50";
        return "50+";
    }


    public long getTotalIssuesDetected() {
        return totalIssuesDetected.sum();
    }

    public long getTotalRequestsAnalyzed() {
        return totalRequestsAnalyzed.sum();
    }

    public long getIssuesByType(IssueType type) {
        LongAdder adder = issuesByType.get(type);
        return adder != null ? adder.sum() : 0;
    }

    public long getIssuesBySeverity(Severity severity) {
        LongAdder adder = issuesBySeverity.get(severity);
        return adder != null ? adder.sum() : 0;
    }

    public long getCurrentQueriesInRequest() {
        return currentQueriesInRequest.get();
    }

    public long getLastDetectionDurationMs() {
        return lastDetectionDurationMs.get();
    }

    public long getQueryCountBucket(String bucket) {
        LongAdder adder = queriesPerRequestBuckets.get(bucket);
        return adder != null ? adder.sum() : 0;
    }

    public long getDetectionDurationBucket(String bucket) {
        LongAdder adder = detectionDurationBuckets.get(bucket);
        return adder != null ? adder.sum() : 0;
    }


    public MetricsSnapshot getSnapshot() {
        return new MetricsSnapshot(
            getTotalIssuesDetected(),
            getTotalRequestsAnalyzed(),
            getCurrentQueriesInRequest(),
            getLastDetectionDurationMs(),
            getIssuesByType(IssueType.N_PLUS_ONE),
            getIssuesByType(IssueType.SLOW_QUERY),
            getIssuesBySeverity(Severity.INFO),
            getIssuesBySeverity(Severity.WARNING),
            getIssuesBySeverity(Severity.ERROR),
            getIssuesBySeverity(Severity.CRITICAL)
        );
    }



    public void reset() {
        synchronized (resetLock) {
            resetting.set(true);
            try {
                totalIssuesDetected.reset();
                totalRequestsAnalyzed.reset();
                currentQueriesInRequest.set(0);
                lastDetectionDurationMs.set(0);
                

                for (LongAdder adder : issuesByType.values()) {
                    adder.reset();
                }
                for (LongAdder adder : issuesBySeverity.values()) {
                    adder.reset();
                }
                for (LongAdder adder : queriesPerRequestBuckets.values()) {
                    adder.reset();
                }
                for (LongAdder adder : detectionDurationBuckets.values()) {
                    adder.reset();
                }
                
                issuesByType.clear();
                issuesBySeverity.clear();
                
            } finally {
                resetting.set(false);
            }
        }
    }


    public static class MetricsSnapshot {
        private final long totalIssues;
        private final long totalRequests;
        private final long currentQueries;
        private final long lastDurationMs;
        private final long nPlusOneCount;
        private final long slowQueryCount;
        private final long infoCount;
        private final long warningCount;
        private final long errorCount;
        private final long criticalCount;

        public MetricsSnapshot(long totalIssues, long totalRequests, long currentQueries,
                             long lastDurationMs, long nPlusOneCount, long slowQueryCount,
                             long infoCount, long warningCount, long errorCount, long criticalCount) {
            this.totalIssues = totalIssues;
            this.totalRequests = totalRequests;
            this.currentQueries = currentQueries;
            this.lastDurationMs = lastDurationMs;
            this.nPlusOneCount = nPlusOneCount;
            this.slowQueryCount = slowQueryCount;
            this.infoCount = infoCount;
            this.warningCount = warningCount;
            this.errorCount = errorCount;
            this.criticalCount = criticalCount;
        }

        public long getTotalIssues() { return totalIssues; }
        public long getTotalRequests() { return totalRequests; }
        public long getCurrentQueries() { return currentQueries; }
        public long getLastDurationMs() { return lastDurationMs; }
        public long getNPlusOneCount() { return nPlusOneCount; }
        public long getSlowQueryCount() { return slowQueryCount; }
        public long getInfoCount() { return infoCount; }
        public long getWarningCount() { return warningCount; }
        public long getErrorCount() { return errorCount; }
        public long getCriticalCount() { return criticalCount; }

        @Override
        public String toString() {
            return String.format("MetricsSnapshot{totalIssues=%d, totalRequests=%d, nPlusOne=%d, slowQuery=%d}",
                    totalIssues, totalRequests, nPlusOneCount, slowQueryCount);
        }
    }
}
