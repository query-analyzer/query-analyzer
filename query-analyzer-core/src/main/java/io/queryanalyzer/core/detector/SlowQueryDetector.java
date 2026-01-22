package io.queryanalyzer.core.detector;

import io.queryanalyzer.core.analyzer.ImprovementEstimator;
import io.queryanalyzer.core.analyzer.ImprovementEstimator.EstimateResult;
import io.queryanalyzer.core.analyzer.StackTraceFilter;
import io.queryanalyzer.core.config.QueryAnalyzerConfig;
import io.queryanalyzer.core.context.RequestContextHolder;
import io.queryanalyzer.core.model.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;


public class SlowQueryDetector implements QueryDetector {

    private final QueryAnalyzerConfig config;
    
    private static final Pattern OR_CONDITION_PATTERN = Pattern.compile("\\bor\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern LEADING_WILDCARD_PATTERN = Pattern.compile("like\\s+['\"]%", Pattern.CASE_INSENSITIVE);

    public SlowQueryDetector(QueryAnalyzerConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Config cannot be null");
        }
        this.config = config;
    }

    @Override
    public String getName() {
        return "slow-query";
    }

    @Override
    public List<QueryIssue> detect(List<QueryInfo> queries) {
        if (queries == null || queries.isEmpty()) {
            return Collections.emptyList();
        }

        List<QueryIssue> issues = new ArrayList<>();

        for (QueryInfo query : queries) {
            if (query.getExecutionTimeMs() >= config.getWarningThresholdMs()) {
                QueryIssue issue = createSlowQueryIssue(query);
                if (issue != null) {
                    issues.add(issue);
                }
            }
        }

        return issues;
    }

    private QueryIssue createSlowQueryIssue(QueryInfo query) {
        String sql = query.getSql();
        if (sql == null || sql.isBlank()) {
            return null;
        }
        
        Severity severity = config.determineSeverity(query.getExecutionTimeMs());
        String location = StackTraceFilter.findApplicationCode(query.getStackTrace());
        List<String> suggestions = generateSuggestions(sql);
        QueryMetrics metrics = createMetrics(query);

        String description = String.format(
            "Query took %dms (threshold: %dms)",
            query.getExecutionTimeMs(),
            config.getWarningThresholdMs()
        );

        return QueryIssue.builder()
            .type(IssueType.SLOW_QUERY)
            .severity(severity)
            .description(description)
            .location(location)
            .endpoint(RequestContextHolder.getEndpoint())
            .httpMethod(RequestContextHolder.getHttpMethod())
            .sampleQuery(sql)
            .suggestions(suggestions)
            .metrics(metrics)
            .detectedAt(Instant.now())
            .build();
    }


    private List<String> generateSuggestions(String sql) {
        List<String> suggestions = new ArrayList<>();
        
        if (sql == null || sql.isEmpty()) {
            suggestions.add("Run EXPLAIN ANALYZE to understand query execution plan");
            return suggestions;
        }
        
        String sqlLower = sql.toLowerCase();

        suggestions.add("Run EXPLAIN ANALYZE to understand query execution plan");
        suggestions.add("Check if appropriate indexes exist on queried columns");

        if (sqlLower.contains("select *")) {
            suggestions.add("Avoid SELECT *, specify only needed columns to reduce data transfer");
        }

        if (LEADING_WILDCARD_PATTERN.matcher(sql).find()) {
            suggestions.add("Avoid leading wildcard in LIKE (e.g., LIKE '%value') - cannot use index");
            suggestions.add("Consider full-text search for text matching");
        }

        if (OR_CONDITION_PATTERN.matcher(sql).find()) {
            suggestions.add("OR conditions may prevent index usage - consider UNION or IN clause");
        }

        if (sqlLower.contains("!=") || sqlLower.contains("<>")) {
            suggestions.add("Inequality operators (!=, <>) may prevent index usage");
        }

        if (!sqlLower.contains("where") && (sqlLower.contains("update ") || sqlLower.contains("delete "))) {
            suggestions.add("UPDATE/DELETE without WHERE clause affects all rows - ensure this is intended");
        }

        if (sqlLower.contains(" join ")) {
            suggestions.add("Verify JOIN conditions are indexed on both tables");
        }

        if (sqlLower.contains("order by") && !sqlLower.contains("limit")) {
            suggestions.add("ORDER BY without LIMIT may require sorting entire result set");
        }

        if (sqlLower.contains("group by")) {
            suggestions.add("Ensure GROUP BY columns are indexed");
        }

        return suggestions;
    }


    private QueryMetrics createMetrics(QueryInfo query) {
        EstimateResult estimate = ImprovementEstimator.estimateSlowQueryImprovement(
            query, 
            config.getWarningThresholdMs()
        );
        
        double improvementPercent = estimate.isReliable() 
            ? estimate.getImprovementPercent() 
            : 0.0;

        return new QueryMetrics(
            query.getExecutionTimeMs(),
            1,
            improvementPercent
        );
    }
}
