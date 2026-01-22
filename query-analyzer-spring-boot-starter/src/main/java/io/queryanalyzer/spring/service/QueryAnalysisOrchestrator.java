package io.queryanalyzer.spring.service;

import io.queryanalyzer.core.context.RequestContext;
import io.queryanalyzer.core.context.RequestContextHolder;
import io.queryanalyzer.core.detector.QueryDetector;
import io.queryanalyzer.core.metrics.MetricsCollector;
import io.queryanalyzer.core.model.*;
import io.queryanalyzer.core.plan.QueryPlanAnalyzer;
import io.queryanalyzer.core.plan.QueryPlanAnalyzerFactory;
import io.queryanalyzer.core.plan.model.QueryPlanResult;
import io.queryanalyzer.core.reporter.QueryReporter;
import io.queryanalyzer.core.storage.IssueStorage;
import io.queryanalyzer.core.storage.model.StoredIssue;
import io.queryanalyzer.spring.config.QueryAnalyzerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;


public class QueryAnalysisOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(QueryAnalysisOrchestrator.class);

    private final List<QueryDetector> detectors;
    private final List<QueryReporter> reporters;
    private final QueryAnalyzerProperties properties;
    private final MetricsCollector metricsCollector;
    private final IssueStorage storage;
    private final DataSource dataSource;
    private final QueryPlanAnalyzerFactory planAnalyzerFactory;
    
    private final AtomicInteger planAnalysisCount = new AtomicInteger(0);
    private volatile long rateLimitWindowStart = System.currentTimeMillis();


    public QueryAnalysisOrchestrator(
        List<QueryDetector> detectors,
        List<QueryReporter> reporters,
        QueryAnalyzerProperties properties,
        MetricsCollector metricsCollector,
        IssueStorage storage,
        DataSource dataSource,
        QueryPlanAnalyzerFactory planAnalyzerFactory) {

        this.detectors = detectors != null ? detectors : new ArrayList<>();
        this.reporters = reporters != null ? reporters : new ArrayList<>();
        this.properties = properties;
        this.metricsCollector = metricsCollector;
        this.storage = storage;
        this.dataSource = dataSource;
        this.planAnalyzerFactory = planAnalyzerFactory;

        log.debug("Orchestrator initialized with {} detectors, {} reporters, MetricsCollector={}, Storage={}, DataSource={}, PlanAnalyzer={}",
            this.detectors.size(), this.reporters.size(), 
            metricsCollector != null, storage != null, 
            dataSource != null, planAnalyzerFactory != null);
    }


    public void analyzeAndReport() {
        if (!properties.isEnabled()) {
            return;
        }

        Instant startTime = Instant.now();
        
        try {
            RequestContext context = RequestContextHolder.get();
            
            if (context == null) {
                log.trace("No request context available for analysis");
                return;
            }
            
            List<QueryInfo> queries = context.getQueries();

            if (queries.isEmpty()) {
                log.trace("No queries to analyze for {} {}", 
                    context.getHttpMethod(), context.getEndpoint());
                return;
            }

            log.debug("Analyzing {} queries for {} {}", 
                queries.size(), context.getHttpMethod(), context.getEndpoint());

            // Run all enabled detectors
            List<QueryIssue> allIssues = new ArrayList<>();
            for (QueryDetector detector : detectors) {
                try {
                    List<QueryIssue> issues = detector.detect(queries);
                    if (issues != null && !issues.isEmpty()) {
                        allIssues.addAll(issues);
                        log.debug("Detector '{}' found {} issues", detector.getName(), issues.size());
                    }
                } catch (Exception e) {
                    log.error("Detector '{}' failed", detector.getName(), e);
                }
            }
            
            // Analyze query plans for ERROR+ severity issues (NEW)
            if (planAnalyzerFactory != null && dataSource != null && !allIssues.isEmpty()) {
                enrichIssuesWithQueryPlans(allIssues);
            }
            
            // Record metrics
            recordMetrics(queries, allIssues, startTime);

            if (!allIssues.isEmpty()) {
                // Report to all reporters
                for (QueryReporter reporter : reporters) {
                    try {
                        reporter.report(allIssues);
                    } catch (Exception e) {
                        log.error("Reporter failed", e);
                    }
                }
                
                // Store issues (NEW)
                if (storage != null && context != null) {
                    storeIssues(allIssues, context);
                }
            } else {
                log.debug("No performance issues detected");
            }

        } catch (Exception e) {
            log.error("Query analysis failed", e);
        }
    }


    private QueryStatistics calculateStatistics(List<QueryInfo> queries) {
        if (queries.isEmpty()) {
            return new QueryStatistics(0, 0, 0, 0, 0, 0);
        }

        long totalTime = queries.stream()
            .mapToLong(QueryInfo::getExecutionTimeMs)
            .sum();

        long avgTime = totalTime / queries.size();

        long maxTime = queries.stream()
            .mapToLong(QueryInfo::getExecutionTimeMs)
            .max()
            .orElse(0);

        long uniqueQueries = queries.stream()
            .map(QueryInfo::getNormalizedSql)
            .distinct()
            .count();

        int repeatedQueries = queries.size() - (int) uniqueQueries;

        return new QueryStatistics(
            queries.size(),
            totalTime,
            avgTime,
            maxTime,
            (int) uniqueQueries,
            repeatedQueries
        );
    }
    

    private void recordMetrics(List<QueryInfo> queries, List<QueryIssue> issues, Instant startTime) {
        if (metricsCollector == null) {
            return;
        }
        
        try {
            long durationMs = Duration.between(startTime, Instant.now()).toMillis();
            
            metricsCollector.recordRequestAnalyzed(queries.size(), durationMs);
            
            for (QueryIssue issue : issues) {
                metricsCollector.recordIssue(issue.getType(), issue.getSeverity());
            }
            
            log.debug("Metrics recorded: {} queries, {} issues, {}ms duration", 
                queries.size(), issues.size(), durationMs);
                
        } catch (Exception e) {
            log.warn("Failed to record metrics", e);
        }
    }
    

    private void storeIssues(List<QueryIssue> issues, RequestContext context) {
        try {
            for (QueryIssue issue : issues) {
                StoredIssue stored = StoredIssue.builder()
                    .id(UUID.randomUUID().toString())
                    .timestamp(Instant.now())
                    .endpoint(context.getEndpoint())
                    .httpMethod(context.getHttpMethod())
                    .type(issue.getType())
                    .severity(issue.getSeverity())
                    .description(issue.getDescription())
                    .queryCount(context.getQueryCount())
                    .executionTimeMs(context.getTotalQueryTimeMs())
                    .sampleQuery(issue.getSampleQuery())
                    .location(issue.getLocation())
                    .requestId(context.getRequestId())
                    .userId(context.getUserId())
                    .planResult(issue.getPlanResult())
                    .build();
                
                storage.store(stored);
            }
            
            log.trace("Stored {} issues for {} {}", 
                issues.size(), context.getHttpMethod(), context.getEndpoint());
                
        } catch (Exception e) {
            log.error("Failed to store issues", e);
            // Don't fail the request - storage is optional
        }
    }
    

    private void enrichIssuesWithQueryPlans(List<QueryIssue> issues) {
        if (!properties.getPlan().isEnabled()) {
            log.trace("Query plan analysis is disabled");
            return;
        }
        
        if (!checkRateLimit()) {
            log.debug("Query plan analysis rate limit exceeded, skipping");
            return;
        }
        
        final int maxPlans = properties.getPlan().getMaxPerRequest();
        
        try (Connection connection = dataSource.getConnection()) {
            Optional<QueryPlanAnalyzer> analyzerOpt = planAnalyzerFactory.getAnalyzer(connection);
            
            if (analyzerOpt.isEmpty()) {
                log.debug("Query plan analyzer not available for this database");
                return;
            }
            
            QueryPlanAnalyzer analyzer = analyzerOpt.get();
            int analyzed = 0;
            
            for (int i = 0; i < issues.size(); i++) {
                if (analyzed >= maxPlans) {
                    log.debug("Max query plans ({}) reached, skipping remaining issues", maxPlans);
                    break;
                }
                
                QueryIssue issue = issues.get(i);
                if (shouldAnalyzePlan(issue)) {
                    QueryPlanResult plan = analyzePlan(analyzer, connection, issue);
                    
                    if (plan != null) {
                        // Use immutable pattern - replace issue in list
                        QueryIssue enrichedIssue = issue.withPlanResult(plan);
                        
                        if (plan.getRecommendations() != null && !plan.getRecommendations().isEmpty()) {
                            enrichedIssue = enrichedIssue.withAdditionalSuggestions(plan.getRecommendations());
                        }
                        
                        issues.set(i, enrichedIssue);
                        analyzed++;
                        
                        log.debug("Query plan analyzed: {}", plan.getSummary());
                    }
                }
            }
            
            if (analyzed > 0) {
                log.info("Analyzed {} query plans", analyzed);
            }
            
        } catch (Exception e) {
            log.error("Failed to enrich issues with query plans", e);
        }
    }
    

    private boolean checkRateLimit() {
        int maxPerMinute = properties.getPlan().getMaxPerMinute();
        
        synchronized (this) {
            long now = System.currentTimeMillis();
            
            if (now - rateLimitWindowStart >= 60000) {
                rateLimitWindowStart = now;
                planAnalysisCount.set(0);
            }
            
            int current = planAnalysisCount.get();
            if (current >= maxPerMinute) {
                return false;
            }
            
            planAnalysisCount.incrementAndGet();
            return true;
        }
    }
    

    private boolean shouldAnalyzePlan(QueryIssue issue) {
        String minSeverityStr = properties.getPlan().getMinSeverity();
        Severity minSeverity;
        
        try {
            minSeverity = Severity.valueOf(minSeverityStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid minSeverity '{}', defaulting to ERROR", minSeverityStr);
            minSeverity = Severity.ERROR;
        }
        
        return isAtLeast(issue.getSeverity(), minSeverity);
    }
    

    private boolean isAtLeast(Severity actual, Severity minimum) {
        return getSeverityLevel(actual) >= getSeverityLevel(minimum);
    }
    
    private int getSeverityLevel(Severity severity) {
        return switch (severity) {
            case INFO -> 0;
            case WARNING -> 1;
            case ERROR -> 2;
            case CRITICAL -> 3;
        };
    }
    

    private QueryPlanResult analyzePlan(
        QueryPlanAnalyzer analyzer, 
        Connection connection, 
        QueryIssue issue) {
        
        try {
            String sql = issue.getSampleQuery();
            if (sql == null || sql.trim().isEmpty()) {
                log.debug("No sample query available for plan analysis");
                return null;
            }
            
            // Analyze the query
            QueryPlanResult result = analyzer.analyze(connection, sql);
            log.debug("Plan analysis completed for issue: {}", issue.getType());
            return result;
            
        } catch (Exception e) {
            log.debug("Failed to analyze query plan: {}", e.getMessage());
            return null;
        }
    }
}
