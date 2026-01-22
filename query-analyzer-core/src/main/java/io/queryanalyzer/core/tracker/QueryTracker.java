package io.queryanalyzer.core.tracker;

import io.queryanalyzer.core.analyzer.SqlNormalizer;
import io.queryanalyzer.core.analyzer.StackTraceFilter;
import io.queryanalyzer.core.context.RequestContextHolder;
import io.queryanalyzer.core.model.QueryInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class QueryTracker {

    private static final Logger log = LoggerFactory.getLogger(QueryTracker.class);

    private QueryTracker() {
        // Utility class
    }


    @Deprecated
    public static void startTracking() {
        if (!RequestContextHolder.isEnabled()) {
            return;
        }

        if (!RequestContextHolder.isActive()) {
            RequestContextHolder.start(null, null);
            log.debug("Started query tracking without HTTP context (legacy mode)");
        }
    }


    public static void recordQuery(String sql, long executionTimeMs) {
        if (!RequestContextHolder.isEnabled() || !RequestContextHolder.isActive()) {
            return;
        }

        try {
            String normalizedSql = SqlNormalizer.normalize(sql);
            StackTraceElement[] fullStackTrace = Thread.currentThread().getStackTrace();
            StackTraceElement[] filteredStackTrace = StackTraceFilter.filter(fullStackTrace);

            // Store full stack trace in metadata for confidence analysis
            // The filtered stack trace is used for location detection
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("fullStackTrace", fullStackTrace);

            QueryInfo queryInfo = new QueryInfo(
                sql,
                normalizedSql,
                executionTimeMs,
                Instant.now(),
                filteredStackTrace,
                Thread.currentThread().getName(),
                metadata
            );

            RequestContextHolder.recordQuery(queryInfo);

        } catch (Exception e) {
            log.error("Failed to record query", e);
        }
    }


    public static List<QueryInfo> getQueries() {
        return RequestContextHolder.getQueries();
    }


    @Deprecated
    public static void setEndpoint(String endpoint) {
        log.trace("setEndpoint() called but endpoint is now set at context creation");
    }


    public static String getEndpoint() {
        return RequestContextHolder.getEndpoint();
    }


    public static void clear() {
        RequestContextHolder.clear();
    }


    public static boolean isTracking() {
        return RequestContextHolder.isActive();
    }


    public static void setEnabled(boolean enabled) {
        RequestContextHolder.setEnabled(enabled);
    }

    public static boolean isEnabled() {
        return RequestContextHolder.isEnabled();
    }
}
