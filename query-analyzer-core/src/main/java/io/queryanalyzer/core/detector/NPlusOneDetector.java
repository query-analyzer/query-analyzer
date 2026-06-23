package io.queryanalyzer.core.detector;

import io.queryanalyzer.core.analyzer.ForeignKeyPatternAnalyzer;
import io.queryanalyzer.core.analyzer.ImprovementEstimator;
import io.queryanalyzer.core.analyzer.InferredRelationship;
import io.queryanalyzer.core.analyzer.StackTraceFilter;
import io.queryanalyzer.core.config.DetectorConfig;
import io.queryanalyzer.core.context.RequestContextHolder;
import io.queryanalyzer.core.detector.confidence.ConfidenceAnalyzer;
import io.queryanalyzer.core.detector.report.NPlusOneReportBuilder;
import io.queryanalyzer.core.detector.timing.TimingAnalyzer;
import io.queryanalyzer.core.model.*;
import io.queryanalyzer.core.suggestion.FrameworkSuggestionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public class NPlusOneDetector implements QueryDetector {
    
    private static final Logger log = LoggerFactory.getLogger(NPlusOneDetector.class);
    
    private static final Pattern[] TIMESTAMP_PATTERNS = {
        Pattern.compile("CREATED_AT\\s*[><=]"),
        Pattern.compile("UPDATED_AT\\s*[><=]"),
        Pattern.compile("TIMESTAMP\\s*[><=]"),
        Pattern.compile("MODIFIED_AT\\s*[><=]"),
        Pattern.compile("EVENT_TIME\\s*[><=]"),
        Pattern.compile("LOG_TIME\\s*[><=]"),
        Pattern.compile("\\w+_AT\\s*[><=]"),
        Pattern.compile("\\w+_TIME\\s*[><=]"),
        Pattern.compile("\\w+_DATE\\s*[><=]")
    };
    
    private final DetectorConfig config;
    private final ConfidenceAnalyzer confidenceAnalyzer;
    private final TimingAnalyzer timingAnalyzer;
    private final NPlusOneReportBuilder reportBuilder;
    
    private final ForeignKeyPatternAnalyzer fkAnalyzer;
    private final FrameworkSuggestionProvider suggestionProvider;


    public NPlusOneDetector(DetectorConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config cannot be null");
        }
        this.config = config;
        this.timingAnalyzer = new TimingAnalyzer(config);
        this.confidenceAnalyzer = new ConfidenceAnalyzer(timingAnalyzer, config);
        this.reportBuilder = new NPlusOneReportBuilder();
        
        this.fkAnalyzer = new ForeignKeyPatternAnalyzer();
        this.suggestionProvider = new FrameworkSuggestionProvider();
    }
    
    @Override
    public String getName() {
        return "n-plus-one";
    }
    

    public NPlusOneDetector(DetectorConfig config,
                           ConfidenceAnalyzer confidenceAnalyzer,
                           TimingAnalyzer timingAnalyzer,
                           NPlusOneReportBuilder reportBuilder) {
        if (config == null) {
            throw new IllegalArgumentException("config cannot be null");
        }
        if (confidenceAnalyzer == null) {
            throw new IllegalArgumentException("confidenceAnalyzer cannot be null");
        }
        if (timingAnalyzer == null) {
            throw new IllegalArgumentException("timingAnalyzer cannot be null");
        }
        if (reportBuilder == null) {
            throw new IllegalArgumentException("reportBuilder cannot be null");
        }
        this.config = config;
        this.confidenceAnalyzer = confidenceAnalyzer;
        this.timingAnalyzer = timingAnalyzer;
        this.reportBuilder = reportBuilder;
        this.fkAnalyzer = new ForeignKeyPatternAnalyzer();
        this.suggestionProvider = new FrameworkSuggestionProvider();
    }

    public List<QueryIssue> detect(List<QueryInfo> queries) {
        if (queries == null || queries.size() < config.getMinRepetitions()) {
            return Collections.emptyList();
        }

        List<QueryInfo> queriesToAnalyze = queries;
        boolean sampled = false;
        
        if (queries.size() > config.getMaxQueriesToAnalyze()) {
            queriesToAnalyze = sampleQueries(queries, config.getMaxQueriesToAnalyze());
            sampled = true;
            log.info("Sampling {} queries from {} total for N+1 analysis. " +
                "High query count may indicate a performance problem.", 
                queriesToAnalyze.size(), queries.size());
        }

        Map<String, List<QueryInfo>> patterns = groupByPattern(queriesToAnalyze);
        
        Map<String, String> tableNames = extractTableNames(patterns);

        List<QueryIssue> issues = new ArrayList<>();
        for (Map.Entry<String, List<QueryInfo>> entry : patterns.entrySet()) {
            List<QueryInfo> patternQueries = entry.getValue();
            
            if (patternQueries.size() >= config.getMinRepetitions()) {
                QueryIssue issue = analyzePattern(patternQueries, tableNames.get(entry.getKey()));
                if (issue != null) {
                    if (sampled) {
                        issue = adjustForSampling(issue, queries.size(), queriesToAnalyze.size());
                    }
                    issues.add(issue);
                }
            }
        }

        return issues;
    }
    
    private List<QueryInfo> sampleQueries(List<QueryInfo> queries, int maxSize) {
        if (queries.size() <= maxSize) {
            return queries;
        }
        
        List<QueryInfo> sampled = new ArrayList<>(maxSize);
        
        int firstHalf = maxSize / 2;
        sampled.addAll(queries.subList(0, firstHalf));
        
        int remaining = maxSize - firstHalf;
        List<QueryInfo> rest = queries.subList(firstHalf, queries.size());
        
        int step = Math.max(1, rest.size() / remaining);
        for (int i = 0; i < rest.size() && sampled.size() < maxSize; i += step) {
            sampled.add(rest.get(i));
        }
        
        return sampled;
    }
    

    private QueryIssue adjustForSampling(QueryIssue issue, int totalQueries, int sampledQueries) {
        double ratio = (double) totalQueries / sampledQueries;
        int estimatedCount = (int) (issue.getMetrics().getQueryCount() * ratio);
        
        String adjustedDescription = String.format(
            "%s (estimated from %d sampled queries, ~%d total)",
            issue.getDescription(), sampledQueries, estimatedCount
        );
        
        QueryMetrics adjustedMetrics = new QueryMetrics(
            (long) (issue.getMetrics().getExecutionTimeMs() * ratio),
            estimatedCount,
            issue.getMetrics().getPotentialImprovementPercent()
        );
        
        return QueryIssue.builder()
            .type(issue.getType())
            .severity(issue.getSeverity())
            .description(adjustedDescription)
            .location(issue.getLocation())
            .endpoint(issue.getEndpoint())
            .httpMethod(issue.getHttpMethod())
            .sampleQuery(issue.getSampleQuery())
            .suggestions(issue.getSuggestions())
            .metrics(adjustedMetrics)
            .detectedAt(issue.getDetectedAt())
            .build();
    }


    private Map<String, List<QueryInfo>> groupByPattern(List<QueryInfo> queries) {
        return queries.stream()
                .collect(Collectors.groupingBy(QueryInfo::getNormalizedSql));
    }
    

    private Map<String, String> extractTableNames(Map<String, List<QueryInfo>> patterns) {
        Map<String, String> tableNames = new HashMap<>();
        for (Map.Entry<String, List<QueryInfo>> entry : patterns.entrySet()) {
            String tableName = extractTableName(entry.getValue().get(0).getSql());
            tableNames.put(entry.getKey(), tableName);
        }
        return tableNames;
    }


    private QueryIssue analyzePattern(List<QueryInfo> queries, String tableName) {
        if (isLikelyLegitimate(queries)) {
            return null;
        }
        
        DetectorConfig.DetectionMode mode = config.getDetectionMode();
        
        boolean thresholdPassed = queries.size() >= config.getMinRepetitions();
        ConfidenceScore confidence = null;
        boolean confidencePassed = false;
        
        if (mode == DetectorConfig.DetectionMode.CONFIDENCE ||
            mode == DetectorConfig.DetectionMode.HYBRID) {
            confidence = confidenceAnalyzer.analyze(queries);
            confidencePassed = confidence.getOverallScore() >= config.getMinConfidenceThreshold();
        }
        
        boolean shouldReport = switch (mode) {
            case THRESHOLD -> thresholdPassed;
            case CONFIDENCE -> confidencePassed;
            case HYBRID -> thresholdPassed && confidencePassed;
        };
        
        if (!shouldReport) {
            if (log.isDebugEnabled()) {
                log.debug("Skipping pattern '{}': mode={}, threshold={}, confidence={}", 
                    tableName, mode, thresholdPassed, 
                    confidence != null ? confidence.getOverallScore() : "N/A");
            }
            return null;
        }

        return buildIssueWithEnhancements(queries, tableName, confidence);
    }
    

    private QueryIssue buildIssueWithEnhancements(
            List<QueryInfo> queries, 
            String tableName, 
            ConfidenceScore confidence) {
        
        String location = findApplicationCode(queries);
        String endpoint = RequestContextHolder.getEndpoint();
        String httpMethod = RequestContextHolder.getHttpMethod();
        
        List<String> suggestions;
        if (confidence != null) {
            NPlusOneReport report = reportBuilder.build(queries, location, tableName, 
                                                        endpoint, confidence);
            suggestions = reportBuilder.formatForConsole(report, confidence);
        } else {
            suggestions = new ArrayList<>();
            suggestions.add("N+1 query pattern detected (threshold mode)");
        }
        
        suggestions = enhanceSuggestions(queries, suggestions);

        QueryMetrics metrics = calculateMetrics(queries);
        Severity severity = determineSeverity(queries);
        
        String sampleQuery = queries.isEmpty() ? null : queries.get(0).getSql();

        return QueryIssue.builder()
                .type(IssueType.N_PLUS_ONE)
                .severity(severity)
                .description(String.format("%d repeated queries detected for '%s'", queries.size(), tableName))
                .location(location)
                .endpoint(endpoint)
                .httpMethod(httpMethod)
                .sampleQuery(sampleQuery)
                .suggestions(suggestions)
                .metrics(metrics)
                .detectedAt(Instant.now())
                .build();
    }
    

    private List<String> enhanceSuggestions(List<QueryInfo> queries, List<String> baseSuggestions) {
        if (queries == null || queries.isEmpty()) {
            return baseSuggestions;
        }
        
        List<String> enhanced = new ArrayList<>(baseSuggestions);
        
        Optional<InferredRelationship> relationship = Optional.empty();
        if (config.isEnableRelationshipInference()) {
            try {
                relationship = fkAnalyzer.inferRelationship(queries);
                
                if (relationship.isPresent()) {
                    InferredRelationship rel = relationship.get();
                    enhanced.add(String.format("Relationship: %s (%.0f%% confidence)",
                        rel.getDescription(), rel.getConfidence() * 100));
                }
            } catch (Exception e) {
                log.debug("Failed to infer relationship: {}", e.getMessage());
            }
        }
        
        if (config.isEnableFrameworkSuggestions()) {
            try {
                StackTraceElement[] stackTrace = queries.get(0).getStackTrace();
                FrameworkSuggestionProvider.Framework framework = 
                    suggestionProvider.detectFramework(stackTrace);
                
                if (framework != FrameworkSuggestionProvider.Framework.UNKNOWN) {
                    List<String> frameworkSuggestions = suggestionProvider.getSuggestions(
                        framework, 
                        relationship.orElse(null), 
                        queries.size()
                    );
                    enhanced.addAll(frameworkSuggestions);
                }
            } catch (Exception e) {
                log.debug("Failed to generate framework suggestions: {}", e.getMessage());
            }
        }
        
        return enhanced;
    }


    private boolean isLikelyLegitimate(List<QueryInfo> queries) {
        if (queries == null || queries.isEmpty()) {
            return true;
        }
        
        String firstSql = queries.get(0).getSql().toUpperCase();
        
        if (hasBatchInClause(firstSql)) {
            return true;
        }

        if (timingAnalyzer.hasDeliberatePacing(queries)) {
            return true;
        }
        
        if (isPaginationPattern(queries)) {
            return true;
        }
        
        if (isStreamingPattern(queries)) {
            return true;
        }

        return false;
    }
    

    private boolean hasBatchInClause(String sql) {
        int inIndex = sql.indexOf(" IN (");
        if (inIndex == -1) {
            inIndex = sql.indexOf(" IN(");
        }
        if (inIndex == -1) {
            return false;
        }
        
        int openParen = sql.indexOf('(', inIndex);
        if (openParen == -1) {
            return false;
        }
        
        int closeParen = findMatchingParen(sql, openParen);
        if (closeParen == -1) {
            return false;
        }
        
        String inContent = sql.substring(openParen + 1, closeParen).trim();
        
        if (inContent.isEmpty()) {
            return false;
        }
        
        int commaCount = countTopLevelCommas(inContent);
        
        return commaCount >= 1;
    }
    

    private int findMatchingParen(String sql, int openIndex) {
        int depth = 1;
        for (int i = openIndex + 1; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }
    

    private int countTopLevelCommas(String content) {
        int count = 0;
        int depth = 0;
        boolean inString = false;
        char stringChar = 0;
        
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            
            if ((c == '\'' || c == '"') && (i == 0 || content.charAt(i - 1) != '\\')) {
                if (!inString) {
                    inString = true;
                    stringChar = c;
                } else if (c == stringChar) {
                    inString = false;
                }
                continue;
            }
            
            if (inString) {
                continue;
            }
            
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == ',' && depth == 0) {
                count++;
            }
        }
        
        return count;
    }
    

    private boolean isPaginationPattern(List<QueryInfo> queries) {
        if (queries.size() < 2) {
            return false;
        }

        try {
            List<Integer> offsets = new ArrayList<>();
            Pattern offsetPattern = Pattern.compile("OFFSET\\s+(\\d+)", Pattern.CASE_INSENSITIVE);

            for (QueryInfo query : queries) {
                java.util.regex.Matcher matcher = offsetPattern.matcher(query.getSql());
                if (matcher.find()) {
                    try {
                        offsets.add(Integer.parseInt(matcher.group(1)));
                    } catch (NumberFormatException e) {
                        // Skip if can't parse
                    }
                }
            }

            if (offsets.size() >= 2) {
                // Literal offsets present (inlined dialects). Sort and check whether they
                // increase by a consistent step - the signature of a pagination scan.
                offsets.sort(Integer::compareTo);

                int firstDiff = offsets.get(1) - offsets.get(0);
                if (firstDiff <= 0) {
                    return false; // Not increasing
                }

                // Verify at least 50% of differences match the first difference
                // (allows for some variation in real pagination)
                int matchingDiffs = 0;
                for (int i = 1; i < offsets.size(); i++) {
                    int diff = offsets.get(i) - offsets.get(i - 1);
                    if (diff == firstDiff) {
                        matchingDiffs++;
                    }
                }

                double matchRatio = (double) matchingDiffs / (offsets.size() - 1);
                return matchRatio >= 0.5;
            }

            // No literal offsets to compare. In real applications pagination
            // offsets/limits are PreparedStatement bind parameters, so the SQL reads
            // "offset ? rows fetch first ? rows only" with nothing to parse. Treat
            // repeated same-shape reads as pagination ONLY when every query carries an
            // OFFSET clause: a bare "LIMIT n" with no OFFSET is NOT pagination - it can
            // be a genuine N+1 of row-limited child fetches - so it must stay reportable.
            return queries.stream()
                .allMatch(q -> q.getSql().toUpperCase().contains("OFFSET"));

        } catch (Exception e) {
            return false;
        }
    }
    

    private boolean isStreamingPattern(List<QueryInfo> queries) {
        if (queries.size() < 3) {
            return false; // Need at least 3 to confirm streaming
        }
        
        try {
            long timestampQueries = queries.stream()
                .filter(q -> hasTimestampWhereClause(q.getSql()))
                .count();
            
            // If 80%+ queries have timestamp WHERE, check timing pattern
            double ratio = (double) timestampQueries / queries.size();
            if (ratio < 0.8) {
                return false;
            }
            
            return hasConsistentTiming(queries);
            
        } catch (Exception e) {
            return false;
        }
    }
    

    private boolean hasTimestampWhereClause(String sql) {
        String upperSql = sql.toUpperCase();
        
        int whereIndex = upperSql.indexOf(" WHERE ");
        if (whereIndex == -1) {
            return false;
        }
        
        String whereClause = upperSql.substring(whereIndex);
        
        for (Pattern pattern : TIMESTAMP_PATTERNS) {
            if (pattern.matcher(whereClause).find()) {
                return true;
            }
        }
        
        return false;
    }
    

    private boolean hasConsistentTiming(List<QueryInfo> queries) {
        if (queries.size() < 3) {
            return false;
        }
        
        List<Long> intervals = new java.util.ArrayList<>();
        for (int i = 1; i < queries.size(); i++) {
            long interval = java.time.Duration.between(
                queries.get(i - 1).getTimestamp(),
                queries.get(i).getTimestamp()
            ).toMillis();
            intervals.add(Math.abs(interval));
        }
        
        double mean = intervals.stream().mapToLong(Long::longValue).average().orElse(0);
        if (mean == 0) {
            return false;
        }
        
        double variance = intervals.stream()
            .mapToDouble(i -> Math.pow(i - mean, 2))
            .average()
            .orElse(0);
        double stdDev = Math.sqrt(variance);
        
        double cv = stdDev / mean;
        return cv < 0.5;
    }


    private String findApplicationCode(List<QueryInfo> queries) {
        QueryInfo firstQuery = queries.get(0);
        String location = StackTraceFilter.findApplicationCode(firstQuery.getStackTrace());

        if ("unknown".equals(location)) {
            String endpoint = RequestContextHolder.getEndpoint();
            if (endpoint != null && !endpoint.isEmpty()) {
                location = "Endpoint: " + endpoint;
            }
        }

        return location;
    }


    private String extractTableName(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return "unknown";
        }

        String upperSql = sql.toUpperCase().trim();
        String originalSql = sql.trim();
        

        if (upperSql.startsWith("WITH ")) {
            int selectIndex = findMainSelect(upperSql);
            if (selectIndex != -1) {
                upperSql = upperSql.substring(selectIndex);
                originalSql = originalSql.substring(selectIndex);
            }
        }
        
        int fromIndex = findFromClause(upperSql);
        if (fromIndex != -1) {
            return extractTableAfterKeyword(originalSql, fromIndex + 6); // 6 = " FROM ".length() - 1
        }
        
        int insertIndex = upperSql.indexOf("INSERT INTO ");
        if (insertIndex != -1) {
            return extractTableAfterKeyword(originalSql, insertIndex + 12);
        }
        
        int updateIndex = upperSql.indexOf("UPDATE ");
        if (updateIndex != -1) {
            return extractTableAfterKeyword(originalSql, updateIndex + 7);
        }
        
        int deleteIndex = upperSql.indexOf("DELETE FROM ");
        if (deleteIndex != -1) {
            return extractTableAfterKeyword(originalSql, deleteIndex + 12);
        }
        
        deleteIndex = upperSql.indexOf("DELETE ");
        if (deleteIndex != -1 && !upperSql.contains("DELETE FROM")) {
            return extractTableAfterKeyword(originalSql, deleteIndex + 7);
        }

        return "unknown";
    }
    

    private int findFromClause(String upperSql) {
        int fromIndex = -1;
        int searchStart = 0;
        
        while (true) {
            fromIndex = upperSql.indexOf(" FROM ", searchStart);
            if (fromIndex == -1) {
                break;
            }
            
            int openParens = 0;
            for (int i = 0; i < fromIndex; i++) {
                char c = upperSql.charAt(i);
                if (c == '(') openParens++;
                else if (c == ')') openParens--;
            }
            
            if (openParens == 0) {
                return fromIndex;
            }
            
            searchStart = fromIndex + 1;
        }
        
        return -1;
    }
    

    private int findMainSelect(String upperSql) {
        // Find the last SELECT that's not inside the CTE definitions
        int depth = 0;
        int lastSelectOutsideCte = -1;
        
        for (int i = 0; i < upperSql.length() - 6; i++) {
            char c = upperSql.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (depth == 0 && upperSql.substring(i).startsWith("SELECT ")) {
                lastSelectOutsideCte = i;
            }
        }
        
        return lastSelectOutsideCte;
    }
    

    private String extractTableAfterKeyword(String sql, int startIndex) {
        if (startIndex >= sql.length()) {
            return "unknown";
        }
        
        String afterKeyword = sql.substring(startIndex).trim();
        
        if (afterKeyword.isEmpty()) {
            return "unknown";
        }
        
        if (afterKeyword.startsWith("(")) {
            return "subquery";
        }
        
        StringBuilder tableName = new StringBuilder();
        boolean inQuote = false;
        char quoteChar = 0;
        
        for (int i = 0; i < afterKeyword.length(); i++) {
            char c = afterKeyword.charAt(i);
            
            if ((c == '"' || c == '\'' || c == '`' || c == '[') && !inQuote) {
                inQuote = true;
                quoteChar = (c == '[') ? ']' : c;
                continue;
            }
            if (inQuote && c == quoteChar) {
                inQuote = false;
                continue;
            }
            
            // Stop at whitespace, comma, or parenthesis if not in quote
            if (!inQuote && (Character.isWhitespace(c) || c == ',' || c == '(' || c == ';')) {
                break;
            }
            
            tableName.append(c);
        }
        
        String result = tableName.toString();
        
        // Handle schema.table notation - extract just the table name
        if (result.contains(".")) {
            String[] parts = result.split("\\.");
            result = parts[parts.length - 1];
        }
        
        // Clean up any remaining quotes
        result = result.replaceAll("[\"'`\\[\\]]", "");
        
        if (result.isEmpty()) {
            return "unknown";
        }
        
        return result.toLowerCase();
    }


    private QueryMetrics calculateMetrics(List<QueryInfo> queries) {
        long totalTime = timingAnalyzer.calculateTotalTime(queries);
        
        if (totalTime == 0) {
            return new QueryMetrics(0, queries.size(), 0.0);
        }
        
        // Use transparent estimation instead of magic numbers
        ImprovementEstimator.EstimateResult estimate = 
            ImprovementEstimator.estimateNPlusOneImprovement(queries, totalTime);

        return new QueryMetrics(
                totalTime,
                queries.size(),
                estimate.getImprovementPercent()
        );
    }


    private Severity determineSeverity(List<QueryInfo> queries) {
        long totalTime = timingAnalyzer.calculateTotalTime(queries);
        int queryCount = queries.size();

        if (totalTime > config.getCriticalTimeMs() || queryCount > config.getCriticalQueryCount()) {
            return Severity.CRITICAL;
        } else if (totalTime > config.getErrorTimeMs() || queryCount > config.getErrorQueryCount()) {
            return Severity.ERROR;
        } else if (totalTime > config.getWarningTimeMs() || queryCount > config.getWarningQueryCount()) {
            return Severity.WARNING;
        } else {
            return Severity.INFO;
        }
    }
}
