package io.queryanalyzer.core.detector.timing;

import io.queryanalyzer.core.config.DetectorConfig;
import io.queryanalyzer.core.model.QueryInfo;

import java.util.ArrayList;
import java.util.List;


public class TimingAnalyzer {
    
    private final DetectorConfig config;
    

    public TimingAnalyzer(DetectorConfig config) {
        this.config = config;
    }

    public TimingPattern analyzePattern(List<QueryInfo> queries) {
        if (queries == null || queries.size() < 2) {
            return TimingPattern.UNKNOWN;
        }
        
        // Check for deliberate pacing first
        if (hasDeliberatePacing(queries)) {
            return TimingPattern.DELIBERATE_PACING;
        }
        
        // Calculate total execution time
        long totalTime = calculateTotalTime(queries);
        
        return TimingPattern.fromTotalTime(totalTime, config);
    }

    public boolean hasDeliberatePacing(List<QueryInfo> queries) {
        if (queries.size() < 2) {
            return false;
        }
        
        List<Long> gaps = calculateGaps(queries);
        
        if (gaps.isEmpty()) {
            return false;
        }
        
        double avgGap = gaps.stream()
            .mapToLong(Long::longValue)
            .average()
            .orElse(0.0);
        
        if (avgGap < config.getDeliberatePacingThresholdMs()) {
            return false;
        }
        
        if (gaps.size() < config.getMinSamplesForVariance()) {
            return true; // avgGap already >= threshold
        }
        
        double variance = calculateVariance(gaps, avgGap);
        double stdDev = Math.sqrt(variance);
        double coefficientOfVariation = stdDev / avgGap;
        
        return coefficientOfVariation <= config.getMaxCoefficientOfVariation();
    }

    private List<Long> calculateGaps(List<QueryInfo> queries) {
        List<Long> gaps = new ArrayList<>();
        
        for (int i = 1; i < queries.size(); i++) {
            QueryInfo prev = queries.get(i - 1);
            QueryInfo curr = queries.get(i);
            
            // Calculate when previous query ended
            long prevEndMs = prev.getTimestamp().toEpochMilli() + prev.getExecutionTimeMs();
            long currStartMs = curr.getTimestamp().toEpochMilli();
            
            // Only count gap if queries executed sequentially (not overlapping)
            if (currStartMs >= prevEndMs) {
                long gap = currStartMs - prevEndMs;
                gaps.add(gap);
            }
        }
        
        return gaps;
    }
    

    private double calculateVariance(List<Long> values, double mean) {
        if (values.size() < 2) {
            return 0.0;
        }
        
        double sumSquaredDiff = 0.0;
        for (Long value : values) {
            double diff = value - mean;
            sumSquaredDiff += diff * diff;
        }
        
        return sumSquaredDiff / (values.size() - 1);
    }

    public long calculateTotalTime(List<QueryInfo> queries) {
        return queries.stream()
                .mapToLong(QueryInfo::getExecutionTimeMs)
                .sum();
    }
}
