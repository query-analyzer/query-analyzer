package io.queryanalyzer.core.detector.report;

import io.queryanalyzer.core.analyzer.ImprovementEstimator;
import io.queryanalyzer.core.analyzer.ImprovementEstimator.EstimateResult;
import io.queryanalyzer.core.model.ConfidenceScore;
import io.queryanalyzer.core.model.NPlusOneReport;
import io.queryanalyzer.core.model.QueryInfo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;


public class NPlusOneReportBuilder {
    

    public NPlusOneReport build(List<QueryInfo> queries, 
                                String location, 
                                String tableName,
                                String endpoint,
                                ConfidenceScore confidence) {
        QueryInfo firstQuery = queries.get(0);
        
        ReportMetrics metrics = calculateMetrics(queries);
        
        List<String> sampleQueries = extractSampleQueries(queries);
        
        return NPlusOneReport.builder()
                .location(location)
                .endpoint(endpoint)
                .threadName(firstQuery.getThreadName())
                .detectedAt(Instant.now())
                .normalizedQuery(firstQuery.getNormalizedSql())
                .tableName(tableName)
                .sampleQueries(sampleQueries)
                .queryCount(queries.size())
                .totalTimeMs(metrics.totalTimeMs)
                .averageTimeMs(metrics.averageTimeMs)
                .estimatedOptimizedTimeMs(metrics.estimatedOptimizedTimeMs)
                .potentialImprovementPercent(metrics.potentialImprovementPercent)
                .build();
    }
    

    private ReportMetrics calculateMetrics(List<QueryInfo> queries) {
        long totalTime = queries.stream()
                .mapToLong(QueryInfo::getExecutionTimeMs)
                .sum();
        
        if (totalTime == 0) {
            return new ReportMetrics(0, 0, 0, 0.0);
        }
        
        double avgTime = (double) totalTime / queries.size();
        
        EstimateResult estimate = ImprovementEstimator.estimateNPlusOneImprovement(queries, totalTime);
        
        double improvementPercent = estimate.getImprovementPercent();
        long estimatedOptimized = (long) (totalTime * (1 - improvementPercent / 100.0));
        
        return new ReportMetrics(
                totalTime,
                Math.round(avgTime),
                estimatedOptimized,
                improvementPercent
        );
    }
    

    private List<String> extractSampleQueries(List<QueryInfo> queries) {
        return queries.stream()
                .limit(3)
                .map(QueryInfo::getSql)
                .map(sql -> sql.length() > 80 ? sql.substring(0, 77) + "..." : sql)
                .toList();
    }
    

    public List<String> formatForConsole(NPlusOneReport report, ConfidenceScore confidence) {
        List<String> output = new ArrayList<>();
        
        // Only add confidence info if available
        if (confidence != null) {
            String confidenceLevel = confidence.getConfidenceLevel().toString();
            int confidencePercent = (int) (confidence.getOverallScore() * 100);
            output.add(String.format("Confidence: %s (%d%%)", confidenceLevel, confidencePercent));
            
            if (confidence.getReasoning() != null && !confidence.getReasoning().isEmpty()) {
                output.add(confidence.getReasoning());
            }
        }
        
        return output;
    }
    

    private static class ReportMetrics {
        final long totalTimeMs;
        final long averageTimeMs;
        final long estimatedOptimizedTimeMs;
        final double potentialImprovementPercent;
        
        ReportMetrics(long totalTimeMs, long averageTimeMs, 
                     long estimatedOptimizedTimeMs, double potentialImprovementPercent) {
            this.totalTimeMs = totalTimeMs;
            this.averageTimeMs = averageTimeMs;
            this.estimatedOptimizedTimeMs = estimatedOptimizedTimeMs;
            this.potentialImprovementPercent = potentialImprovementPercent;
        }
    }
}
