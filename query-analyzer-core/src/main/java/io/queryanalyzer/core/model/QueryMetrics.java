package io.queryanalyzer.core.model;

import lombok.Value;


@Value
public class QueryMetrics {
    long executionTimeMs;
    int queryCount;
    double estimatedImprovementPercent;

    public QueryMetrics(
        long executionTimeMs,
        int queryCount,
        double estimatedImprovementPercent) {
        
        if (executionTimeMs < 0) {
            throw new IllegalArgumentException("Execution time cannot be negative");
        }
        if (queryCount < 0) {
            throw new IllegalArgumentException("Query count cannot be negative");
        }
        if (estimatedImprovementPercent < 0 || estimatedImprovementPercent > 100) {
            throw new IllegalArgumentException(
                "Improvement percentage must be between 0 and 100");
        }
        
        this.executionTimeMs = executionTimeMs;
        this.queryCount = queryCount;
        this.estimatedImprovementPercent = estimatedImprovementPercent;
    }
    

    public double getPotentialImprovementPercent() {
        return estimatedImprovementPercent;
    }
}
