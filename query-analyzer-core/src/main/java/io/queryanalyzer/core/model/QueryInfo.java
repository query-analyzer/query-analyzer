package io.queryanalyzer.core.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;


@Getter
public final class QueryInfo {

    @NonNull
    private final String sql;
    
    @NonNull
    private final String normalizedSql;
    
    private final long executionTimeMs;
    
    @NonNull
    private final Instant timestamp;
    
    @Getter(AccessLevel.NONE)
    private final StackTraceElement[] stackTrace;
    
    private final String threadName;
    
    private final Map<String, Object> metadata;

    public QueryInfo(
        String sql,
        String normalizedSql,
        long executionTimeMs,
        Instant timestamp,
        StackTraceElement[] stackTrace,
        String threadName,
        Map<String, Object> metadata) {

        if (sql == null || sql.trim().isEmpty()) {
            throw new IllegalArgumentException("SQL cannot be null or empty");
        }
        if (normalizedSql == null || normalizedSql.trim().isEmpty()) {
            throw new IllegalArgumentException("Normalized SQL cannot be null or empty");
        }
        if (executionTimeMs < 0) {
            throw new IllegalArgumentException("Execution time cannot be negative");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("Timestamp cannot be null");
        }

        this.sql = sql;
        this.normalizedSql = normalizedSql;
        this.executionTimeMs = executionTimeMs;
        this.timestamp = timestamp;
        this.stackTrace = stackTrace != null ? stackTrace.clone() : new StackTraceElement[0];
        this.threadName = threadName != null ? threadName : "unknown";
        this.metadata = metadata != null
            ? Collections.unmodifiableMap(new HashMap<>(metadata))
            : Collections.emptyMap();
    }


    public static Builder builder() {
        return new Builder();
    }

    public StackTraceElement[] getStackTrace() {
        return stackTrace.clone();
    }
    

    public String getStackTraceString() {
        if (stackTrace == null || stackTrace.length == 0) {
            return "";
        }
        return Arrays.stream(stackTrace)
            .map(StackTraceElement::toString)
            .collect(Collectors.joining("\n"));
    }

    // No equals() and hashCode() -  its my intentional design decision
    // QueryInfo uses identity equality, which is appropriate since:
    // It's not used as a Map key or in Sets
    // Each query execution is a unique event
    // Identity equality is simpler and avoids bugs

    @Override
    public String toString() {
        return "QueryInfo{" +
            "sql='" + (sql.length() > 50 ? sql.substring(0, 50) + "..." : sql) + '\'' +
            ", executionTimeMs=" + executionTimeMs +
            ", timestamp=" + timestamp +
            ", threadName='" + threadName + '\'' +
            '}';
    }


    public static class Builder {
        private String sql;
        private String normalizedSql;
        private long executionTimeMs;
        private Instant timestamp = Instant.now();
        private StackTraceElement[] stackTrace;
        private String threadName;
        private Map<String, Object> metadata;

        public Builder sql(String sql) {
            this.sql = sql;
            return this;
        }

        public Builder normalizedSql(String normalizedSql) {
            this.normalizedSql = normalizedSql;
            return this;
        }

        public Builder executionTimeMs(long executionTimeMs) {
            this.executionTimeMs = executionTimeMs;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder stackTrace(StackTraceElement[] stackTrace) {
            this.stackTrace = stackTrace;
            return this;
        }

        public Builder threadName(String threadName) {
            this.threadName = threadName;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public QueryInfo build() {
            return new QueryInfo(sql, normalizedSql, executionTimeMs, timestamp, stackTrace, threadName, metadata);
        }
    }
}
