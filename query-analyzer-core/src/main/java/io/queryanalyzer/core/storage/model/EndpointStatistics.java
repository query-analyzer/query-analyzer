package io.queryanalyzer.core.storage.model;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;


@Value
@Builder
public class EndpointStatistics {
    

    String endpoint;
    

    int totalIssues;
    

    long criticalCount;
    

    long errorCount;
    

    long warningCount;
    

    long infoCount;
    

    double avgQueriesPerRequest;
    

    double avgExecutionTimeMs;
    

    Instant lastSeen;
    

    Instant firstSeen;
    

    public boolean hasCriticalIssues() {
        return criticalCount > 0;
    }
    

    public boolean hasHighSeverityIssues() {
        return criticalCount > 0 || errorCount > 0;
    }
    

    public long getPriorityScore() {
        return (criticalCount * 100) + (errorCount * 10) + warningCount;
    }
    

    public String getSummary() {
        return String.format("%s: %d issues (C:%d E:%d W:%d I:%d) avg %.1f queries, %.1fms",
            endpoint,
            totalIssues,
            criticalCount,
            errorCount,
            warningCount,
            infoCount,
            avgQueriesPerRequest,
            avgExecutionTimeMs);
    }
}
