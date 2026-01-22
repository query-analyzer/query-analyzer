package io.queryanalyzer.core.model;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;

import java.time.Instant;
import java.util.List;


@Getter
@Builder
public final class NPlusOneReport {

    @NonNull
    private final String location;
    
    // endpoint can be null - passed from detector
    private final String endpoint;
    
    @NonNull
    private final String threadName;
    
    @NonNull
    private final Instant detectedAt;

    @NonNull
    private final String normalizedQuery;
    
    @NonNull
    private final String tableName;
    
    @Singular
    private final List<String> sampleQueries;

    private final int queryCount;
    private final long totalTimeMs;
    private final long averageTimeMs;
    private final long estimatedOptimizedTimeMs;
    private final double potentialImprovementPercent;

    public long getPotentialSavingsMs() {
        return totalTimeMs - estimatedOptimizedTimeMs;
    }
}