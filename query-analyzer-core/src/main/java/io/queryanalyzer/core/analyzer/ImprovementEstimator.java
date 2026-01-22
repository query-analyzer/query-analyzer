package io.queryanalyzer.core.analyzer;

import io.queryanalyzer.core.model.QueryInfo;

import java.util.ArrayList;
import java.util.List;

public final class ImprovementEstimator {

    private ImprovementEstimator() {
    }


    public static final class EstimateResult {
        private final double improvementPercent;
        private final Confidence confidence;
        private final String explanation;
        private final List<String> assumptions;

        public EstimateResult(double improvementPercent, Confidence confidence, 
                              String explanation, List<String> assumptions) {
            this.improvementPercent = Math.max(0, Math.min(100, improvementPercent));
            this.confidence = confidence;
            this.explanation = explanation;
            this.assumptions = assumptions != null ? assumptions : new ArrayList<>();
        }

        public double getImprovementPercent() {
            return improvementPercent;
        }

        public Confidence getConfidence() {
            return confidence;
        }

        public String getExplanation() {
            return explanation;
        }

        public List<String> getAssumptions() {
            return assumptions;
        }
        
        public boolean isReliable() {
            return confidence == Confidence.HIGH || confidence == Confidence.MEDIUM;
        }
    }


    public enum Confidence {
        HIGH("Based on proven optimization patterns"),
        
        MEDIUM("Based on typical optimization results"),
        
        LOW("Rough estimate - actual results may vary significantly"),
        
        UNKNOWN("Unable to estimate - run EXPLAIN ANALYZE for accurate assessment");

        private final String description;

        Confidence(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }


    public static EstimateResult estimateNPlusOneImprovement(List<QueryInfo> queries, long totalTimeMs) {
        if (queries == null || queries.isEmpty() || totalTimeMs <= 0) {
            return new EstimateResult(0, Confidence.UNKNOWN,
                "No queries to analyze",
                List.of());
        }

        int queryCount = queries.size();
        
        if (queryCount < 2) {
            return new EstimateResult(0, Confidence.UNKNOWN,
                "Single query - no N+1 pattern",
                List.of());
        }

        // N+1 optimization: N queries become 1-2 queries
        // Time savings = (N-1)/N of total time, minus overhead for larger query
        // Conservative estimate: assume optimized query takes 2x single query time
        double avgQueryTime = totalTimeMs / (double) queryCount;
        double estimatedOptimizedTime = avgQueryTime * 2;
        double savings = totalTimeMs - estimatedOptimizedTime;
        double improvementPercent = (savings / totalTimeMs) * 100.0;
        
        improvementPercent = Math.max(0, Math.min(95, improvementPercent));

        List<String> assumptions = new ArrayList<>();
        assumptions.add(String.format("Assumes %d queries can be batched into 1-2 queries", queryCount));
        assumptions.add("Assumes network round-trip time is the dominant factor");
        assumptions.add("Optimized query estimated at 2x single query time for larger result set");

        Confidence confidence;
        String explanation;
        
        if (queryCount >= 10) {
            confidence = Confidence.HIGH;
            explanation = String.format(
                "Batching %d queries into 1-2 queries eliminates %d round trips. " +
                "Estimated savings: %.0f%% of total time (%.0fms → ~%.0fms)",
                queryCount, queryCount - 1, improvementPercent, 
                (double) totalTimeMs, estimatedOptimizedTime);
        } else if (queryCount >= 5) {
            confidence = Confidence.MEDIUM;
            explanation = String.format(
                "Batching %d queries reduces round trips. " +
                "Estimated improvement: %.0f%%",
                queryCount, improvementPercent);
        } else {
            confidence = Confidence.LOW;
            explanation = String.format(
                "Only %d repeated queries detected. " +
                "Improvement estimate (%.0f%%) may not justify optimization effort.",
                queryCount, improvementPercent);
        }

        return new EstimateResult(improvementPercent, confidence, explanation, assumptions);
    }

    public static EstimateResult estimateSlowQueryImprovement(QueryInfo query, long thresholdMs) {
        if (query == null) {
            return new EstimateResult(0, Confidence.UNKNOWN,
                "No query to analyze",
                List.of());
        }

        String sql = query.getSql().toLowerCase();
        long executionTime = query.getExecutionTimeMs();

        // Analyze SQL patterns to estimate improvement potential
        SqlPatternAnalysis analysis = analyzeSqlPatterns(sql);
        
        List<String> assumptions = new ArrayList<>();
        assumptions.add("Estimate based on SQL pattern analysis only");
        assumptions.add("Run EXPLAIN ANALYZE for accurate assessment");
        assumptions.add("Actual improvement depends on data distribution and existing indexes");


        if (analysis.hasHighOptimizationPotential()) {
            assumptions.add("Detected patterns commonly associated with missing indexes");
            return new EstimateResult(
                analysis.estimatedImprovement,
                Confidence.LOW,
                analysis.explanation,
                assumptions
            );
        } else if (analysis.hasMediumOptimizationPotential()) {
            return new EstimateResult(
                analysis.estimatedImprovement,
                Confidence.LOW,
                analysis.explanation,
                assumptions
            );
        } else {
            return new EstimateResult(
                0,
                Confidence.UNKNOWN,
                "Cannot estimate improvement without query execution plan. " +
                "Run EXPLAIN ANALYZE to identify optimization opportunities.",
                assumptions
            );
        }
    }

    private static SqlPatternAnalysis analyzeSqlPatterns(String sql) {
        List<String> issues = new ArrayList<>();
        double estimatedImprovement = 0;
        int issueCount = 0;

        if (sql.contains("select *")) {
            issues.add("SELECT * retrieves unnecessary columns");
            estimatedImprovement += 10;
            issueCount++;
        }

        if (sql.contains("like '%") || sql.contains("like \"%")) {
            issues.add("Leading wildcard LIKE prevents index usage");
            estimatedImprovement += 30;
            issueCount++;
        }

        if ((sql.contains("select") || sql.contains("update") || sql.contains("delete"))
            && !sql.contains("where") && !sql.contains("limit")) {
            issues.add("No WHERE clause - full table scan likely");
            estimatedImprovement += 40;
            issueCount++;
        }

        // Multiple JOINs without clear indexing hints
        int joinCount = countOccurrences(sql, " join ");
        if (joinCount >= 3) {
            issues.add(String.format("%d JOINs detected - verify all join columns are indexed", joinCount));
            estimatedImprovement += 20;
            issueCount++;
        }

        // ORDER BY without LIMIT
        if (sql.contains("order by") && !sql.contains("limit")) {
            issues.add("ORDER BY without LIMIT may sort entire result set");
            estimatedImprovement += 15;
            issueCount++;
        }

        // Subqueries in WHERE
        if (sql.contains("where") && sql.contains("select") &&
            sql.indexOf("select", sql.indexOf("where")) > 0) {
            issues.add("Subquery in WHERE clause - consider JOIN or EXISTS");
            estimatedImprovement += 25;
            issueCount++;
        }

        if (issueCount > 1) {
            estimatedImprovement = Math.min(estimatedImprovement, 60);
        }
        estimatedImprovement = Math.min(estimatedImprovement, 80);

        String explanation;
        if (issues.isEmpty()) {
            explanation = "No obvious optimization patterns detected in SQL. " +
                         "Run EXPLAIN ANALYZE to identify bottlenecks.";
        } else {
            explanation = "Detected patterns: " + String.join("; ", issues) + ". " +
                         String.format("Rough estimate: %.0f%% improvement potential.", estimatedImprovement);
        }

        return new SqlPatternAnalysis(issues, estimatedImprovement, explanation);
    }

    private static int countOccurrences(String str, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = str.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }


    private static class SqlPatternAnalysis {
        final List<String> detectedIssues;
        final double estimatedImprovement;
        final String explanation;

        SqlPatternAnalysis(List<String> issues, double improvement, String explanation) {
            this.detectedIssues = issues;
            this.estimatedImprovement = improvement;
            this.explanation = explanation;
        }

        boolean hasHighOptimizationPotential() {
            return estimatedImprovement >= 30;
        }

        boolean hasMediumOptimizationPotential() {
            return estimatedImprovement >= 10 && estimatedImprovement < 30;
        }
    }
}
