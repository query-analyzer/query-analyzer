package io.queryanalyzer.core.model;

import lombok.Value;


@Value
public class QueryStatistics {
    int totalQueries;
    long totalExecutionTimeMs;
    long averageExecutionTimeMs;
    long maxExecutionTimeMs;
    int uniqueQueries;
    int repeatedQueries;


    public QueryStatistics(
        int totalQueries,
        long totalExecutionTimeMs,
        long averageExecutionTimeMs,
        long maxExecutionTimeMs,
        int uniqueQueries,
        int repeatedQueries) {

        if (totalQueries < 0) {
            throw new IllegalArgumentException("Total queries cannot be negative");
        }
        if (totalExecutionTimeMs < 0) {
            throw new IllegalArgumentException("Total execution time cannot be negative");
        }
        if (averageExecutionTimeMs < 0) {
            throw new IllegalArgumentException("Average execution time cannot be negative");
        }
        if (maxExecutionTimeMs < 0) {
            throw new IllegalArgumentException("Max execution time cannot be negative");
        }
        if (uniqueQueries < 0) {
            throw new IllegalArgumentException("Unique queries cannot be negative");
        }
        if (repeatedQueries < 0) {
            throw new IllegalArgumentException("Repeated queries cannot be negative");
        }

        this.totalQueries = totalQueries;
        this.totalExecutionTimeMs = totalExecutionTimeMs;
        this.averageExecutionTimeMs = averageExecutionTimeMs;
        this.maxExecutionTimeMs = maxExecutionTimeMs;
        this.uniqueQueries = uniqueQueries;
        this.repeatedQueries = repeatedQueries;
    }
}
