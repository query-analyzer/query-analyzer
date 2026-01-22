package io.queryanalyzer.test;

import io.queryanalyzer.core.analyzer.SqlNormalizer;
import io.queryanalyzer.core.config.DetectorConfig;
import io.queryanalyzer.core.detector.NPlusOneDetector;
import io.queryanalyzer.core.model.QueryInfo;
import io.queryanalyzer.core.model.QueryIssue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;


public final class QueryRecorder {
    
    private static final ThreadLocal<List<QueryInfo>> QUERIES = new ThreadLocal<>();
    
    private QueryRecorder() {}
    

    public static void start() {
        QUERIES.set(new ArrayList<>());
    }

    public static void record(String sql) {
        record(sql, null, 0);
    }
    

    public static void record(String sql, long executionTimeMs) {
        record(sql, null, executionTimeMs);
    }

    public static void record(String sql, StackTraceElement[] stackTrace, long executionTimeMs) {
        List<QueryInfo> queries = QUERIES.get();
        if (queries == null || sql == null || sql.trim().isEmpty()) {
            return;
        }
        
        String normalizedSql = SqlNormalizer.normalize(sql);
        if (normalizedSql == null || normalizedSql.trim().isEmpty()) {
            normalizedSql = sql.toLowerCase().trim();
        }
        
        StackTraceElement[] stack = stackTrace;
        if (stack == null) {
            stack = Thread.currentThread().getStackTrace();
        }
        
        QueryInfo queryInfo = new QueryInfo(
            sql,
            normalizedSql,
            Math.max(0, executionTimeMs),
            Instant.now(),
            stack,
            Thread.currentThread().getName(),
            null
        );
        
        queries.add(queryInfo);
    }
    

    public static boolean isRecording() {
        return QUERIES.get() != null;
    }
    

    public static List<QueryIssue> stopAndAnalyze(int threshold, Set<String> ignoreTables) {
        List<QueryInfo> queries = QUERIES.get();
        QUERIES.remove();
        
        if (queries == null || queries.isEmpty()) {
            return List.of();
        }
        
        List<QueryInfo> filtered = filterIgnoredTables(queries, ignoreTables);
        
        DetectorConfig config = DetectorConfig.builder()
            .detectionMode(DetectorConfig.DetectionMode.THRESHOLD)
            .minRepetitions(threshold)
            .build();
        
        NPlusOneDetector detector = new NPlusOneDetector(config);
        return detector.detect(filtered);
    }
    

    public static List<QueryIssue> stopAndAnalyze() {
        return stopAndAnalyze(3, Set.of());
    }
    

    public static void stop() {
        QUERIES.remove();
    }

    public static int getRecordedCount() {
        List<QueryInfo> queries = QUERIES.get();
        return queries != null ? queries.size() : 0;
    }
    
    private static List<QueryInfo> filterIgnoredTables(List<QueryInfo> queries, Set<String> ignoreTables) {
        if (ignoreTables == null || ignoreTables.isEmpty()) {
            return queries;
        }
        
        List<QueryInfo> filtered = new ArrayList<>();
        for (QueryInfo query : queries) {
            boolean shouldIgnore = false;
            String sqlLower = query.getSql().toLowerCase();
            
            for (String ignoreTable : ignoreTables) {
                if (sqlLower.contains(ignoreTable.toLowerCase())) {
                    shouldIgnore = true;
                    break;
                }
            }
            
            if (!shouldIgnore) {
                filtered.add(query);
            }
        }
        
        return filtered;
    }
}
