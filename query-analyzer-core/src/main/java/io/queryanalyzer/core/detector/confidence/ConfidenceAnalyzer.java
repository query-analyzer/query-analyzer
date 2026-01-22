package io.queryanalyzer.core.detector.confidence;

import io.queryanalyzer.core.config.DetectorConfig;
import io.queryanalyzer.core.detector.timing.TimingAnalyzer;
import io.queryanalyzer.core.detector.timing.TimingPattern;
import io.queryanalyzer.core.model.ConfidenceScore;
import io.queryanalyzer.core.model.QueryInfo;

import java.util.ArrayList;
import java.util.List;


public class ConfidenceAnalyzer {
    
    private final TimingAnalyzer timingAnalyzer;
    private final DetectorConfig config;
    
    public ConfidenceAnalyzer(TimingAnalyzer timingAnalyzer, DetectorConfig config) {
        this.timingAnalyzer = timingAnalyzer;
        this.config = config;
    }
    

    public ConfidenceScore analyze(List<QueryInfo> queries) {
        if (queries == null || queries.isEmpty()) {
            return buildZeroConfidence("No queries to analyze");
        }
        
        double stackTraceScore = analyzeStackTraces(queries);
        double timingScore = analyzeTimingPattern(queries);
        double patternScore = analyzeQueryPattern(queries);
        
        double overallScore =
            (stackTraceScore * config.getStackTraceWeight()) +
            (timingScore * config.getTimingWeight()) +
            (patternScore * config.getPatternWeight());
        
        return ConfidenceScore.builder()
            .overallScore(overallScore)
            .stackTraceScore(stackTraceScore)
            .timingScore(timingScore)
            .patternScore(patternScore)
            .reasoning(determineReason(stackTraceScore, timingScore, patternScore))
            .build();
    }
    
    private String determineReason(double stackTrace, double timing, double pattern) {
        List<String> factors = new ArrayList<>();
        
        if (stackTrace > 0.7) {
            factors.add("ORM/JDBC framework lazy loading detected in stack traces");
        } else if (stackTrace > 0.3) {
            factors.add("Some framework indicators present");
        }
        
        if (timing > 0.7) {
            factors.add("Queries executed in tight loop");
        } else if (timing > 0.3) {
            factors.add("Queries executed in rapid succession");
        }
        
        if (pattern > 0.7) {
            factors.add("Queries from same code location");
        } else if (pattern > 0.5) {
            factors.add("Queries from same class/method");
        }
        
        return factors.isEmpty() ? "Low confidence indicators" : String.join("; ", factors);
    }
    
    private ConfidenceScore buildZeroConfidence(String reason) {
        return ConfidenceScore.builder()
            .overallScore(0.0)
            .stackTraceScore(0.0)
            .timingScore(0.0)
            .patternScore(0.0)
            .reasoning(reason)
            .build();
    }
    

    private double analyzeStackTraces(List<QueryInfo> queries) {
        int highConfidenceMatches = 0;
        int mediumConfidenceMatches = 0;
        int lowConfidenceMatches = 0;
        
        for (QueryInfo query : queries) {
            FrameworkIndicatorResult result = detectFrameworkIndicators(query);
            if (result == FrameworkIndicatorResult.HIGH) {
                highConfidenceMatches++;
            } else if (result == FrameworkIndicatorResult.MEDIUM) {
                mediumConfidenceMatches++;
            } else if (result == FrameworkIndicatorResult.LOW) {
                lowConfidenceMatches++;
            }
        }
        
        // Calculate score based on what patterns were found
        double ratio;
        if (highConfidenceMatches > 0) {
            ratio = (double) highConfidenceMatches / queries.size();
            if (ratio > 0.8) return 1.0;
            if (ratio > 0.5) return 0.9;
            return 0.7;
        } else if (mediumConfidenceMatches > 0) {
            ratio = (double) mediumConfidenceMatches / queries.size();
            if (ratio > 0.8) return 0.7;
            if (ratio > 0.5) return 0.5;
            return 0.3;
        } else if (lowConfidenceMatches > 0) {
            // Database driver patterns only - this is expected for any query
            // Don't boost confidence just because we see H2/HikariCP
            return 0.0;
        }
        
        return 0.0;
    }
    
    private enum FrameworkIndicatorResult {
        HIGH,    // Definite ORM lazy loading (Hibernate collections, proxies)
        MEDIUM,  // ORM framework present (Spring Data JPA, repository patterns)
        LOW,     // Only database driver patterns (H2, HikariCP) - not meaningful
        NONE     // No framework indicators
    }
    
    /**
     * Detects framework indicators with tiered confidence levels.
     * HIGH: Hibernate lazy loading patterns (PersistentBag, LazyInitializer)
     * MEDIUM: ORM framework patterns (Spring Data JPA, JdbcTemplate)
     * LOW: Database driver only (H2, HikariCP) - present in ALL queries
     */
    private FrameworkIndicatorResult detectFrameworkIndicators(QueryInfo query) {
        StackTraceElement[] stackTrace = getFullStackTrace(query);
        
        if (stackTrace == null || stackTrace.length == 0) {
            return FrameworkIndicatorResult.NONE;
        }
        
        String stackTraceStr = formatStackTrace(stackTrace);
        
        // === HIGH CONFIDENCE: Hibernate Lazy Loading Patterns ===
        // These indicate actual lazy loading, not just any query
        
        // Hibernate collection lazy loading (definite N+1 indicator)
        if (stackTraceStr.contains("PersistentBag")
            || stackTraceStr.contains("PersistentSet")
            || stackTraceStr.contains("PersistentList")
            || stackTraceStr.contains("AbstractPersistentCollection")) {
            return FrameworkIndicatorResult.HIGH;
        }
        
        // Hibernate proxy lazy loading
        if (stackTraceStr.contains("LazyInitializer")
            || stackTraceStr.contains("ByteBuddyInterceptor")
            || stackTraceStr.contains("HibernateProxy")
            || stackTraceStr.contains("org.hibernate.proxy")) {
            return FrameworkIndicatorResult.HIGH;
        }
        
        // Hibernate collection initialization
        if (stackTraceStr.contains("org.hibernate.collection")) {
            return FrameworkIndicatorResult.HIGH;
        }
        
        // === MEDIUM CONFIDENCE: ORM Framework Patterns ===
        // Framework is being used, but not necessarily lazy loading
        
        // Spring Data JPA repository execution
        if (stackTraceStr.contains("SimpleJpaRepository")
            || stackTraceStr.contains("JpaRepository")
            || stackTraceStr.contains("RepositoryMethodInvoker")
            || stackTraceStr.contains("QueryExecutorMethodInterceptor")) {
            return FrameworkIndicatorResult.MEDIUM;
        }
        
        // Hibernate query/loader (could be explicit query, not lazy loading)
        if (stackTraceStr.contains("org.hibernate.loader")
            || stackTraceStr.contains("org.hibernate.query")
            || stackTraceStr.contains("org.hibernate.sql")) {
            return FrameworkIndicatorResult.MEDIUM;
        }
        
        // Spring ORM
        if (stackTraceStr.contains("org.springframework.orm.jpa")
            || stackTraceStr.contains("SharedEntityManagerInvocationHandler")) {
            return FrameworkIndicatorResult.MEDIUM;
        }
        
        // Spring JDBC Template
        if (stackTraceStr.contains("JdbcTemplate")
            || stackTraceStr.contains("NamedParameterJdbcTemplate")) {
            return FrameworkIndicatorResult.MEDIUM;
        }
        
        // MyBatis
        if (stackTraceStr.contains("org.apache.ibatis")
            || stackTraceStr.contains("org.mybatis")
            || stackTraceStr.contains("MapperProxy")) {
            return FrameworkIndicatorResult.MEDIUM;
        }
        
        // jOOQ
        if (stackTraceStr.contains("org.jooq.impl")) {
            return FrameworkIndicatorResult.MEDIUM;
        }
        
        // === LOW CONFIDENCE: Database Driver Patterns ===
        // These are present in ALL database queries, not meaningful for N+1 detection
        
        // H2 database (common in tests)
        if (stackTraceStr.contains("org.h2.jdbc")
            || stackTraceStr.contains("org.h2.command")
            || stackTraceStr.contains("org.h2.engine")) {
            return FrameworkIndicatorResult.LOW;
        }
        
        // HikariCP connection pool
        if (stackTraceStr.contains("com.zaxxer.hikari")
            || stackTraceStr.contains("HikariProxy")) {
            return FrameworkIndicatorResult.LOW;
        }
        
        // PostgreSQL driver
        if (stackTraceStr.contains("org.postgresql.jdbc")) {
            return FrameworkIndicatorResult.LOW;
        }
        
        // MySQL driver
        if (stackTraceStr.contains("com.mysql.cj.jdbc")) {
            return FrameworkIndicatorResult.LOW;
        }
        
        return FrameworkIndicatorResult.NONE;
    }
    
    /**
     * Checks if the query's stack trace indicates it was triggered by a framework.
     * @deprecated Use detectFrameworkIndicators instead for tiered detection
     */
    @Deprecated
    private boolean hasFrameworkIndicators(QueryInfo query) {
        return detectFrameworkIndicators(query) != FrameworkIndicatorResult.NONE;
    }
    
    private String formatStackTrace(StackTraceElement[] stackTrace) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : stackTrace) {
            sb.append(element.toString()).append("\n");
        }
        return sb.toString();
    }
    
    /**
     * Gets the full (unfiltered) stack trace from query metadata.
     * Falls back to the filtered stack trace if full is not available.
     */
    private StackTraceElement[] getFullStackTrace(QueryInfo query) {
        if (query == null) {
            return new StackTraceElement[0];
        }
        
        // Try to get full stack trace from metadata
        if (query.getMetadata() != null && query.getMetadata().containsKey("fullStackTrace")) {
            Object fullTrace = query.getMetadata().get("fullStackTrace");
            if (fullTrace instanceof StackTraceElement[]) {
                return (StackTraceElement[]) fullTrace;
            }
        }
        
        // Fall back to the regular (filtered) stack trace
        return query.getStackTrace();
    }
    

    private double analyzeTimingPattern(List<QueryInfo> queries) {
        TimingPattern pattern = timingAnalyzer.analyzePattern(queries);
        
        if (pattern.isSuspicious()) {
            if (pattern == TimingPattern.TIGHT_LOOP) {
                return 1.0;
            } else if (pattern == TimingPattern.MODERATE_LOOP) {
                return 0.7;
            }
        }
        
        return 0.3;
    }
    
    private double analyzeQueryPattern(List<QueryInfo> queries) {
        try {
            // Level 1: Check if all from exact same line (perfect match)
            long distinctExactLocations = countDistinctLocations(queries, 0, true);
            if (distinctExactLocations == 1) {
                return 1.0;
            }
            
            // Level 2: Check if all from same method (strong signal)
            long distinctMethods = countDistinctLocations(queries, 0, false);
            if (distinctMethods == 1) {
                return 0.9;
            }
            
            // Level 3: Check if all from same class (good signal)
            long distinctClasses = queries.stream()
                .map(this::getStackClass)
                .filter(c -> c != null && !c.equals("unknown"))
                .distinct()
                .count();
            
            if (distinctClasses == 1) {
                return 0.8;
            }
            
            // Level 4: Check for loop pattern (queries from different iterations)
            if (hasLoopPattern(queries)) {
                return 0.7;
            }
            
            // Level 5: Different locations (weakest signal)
            return 0.5;
            
        } catch (Exception e) {
            // if anything fails, return safe default
            return 0.5;
        }
    }
    

    private long countDistinctLocations(List<QueryInfo> queries, int depth, boolean includeLineNumber) {
        return queries.stream()
            .map(q -> getStackLocation(q, depth, includeLineNumber))
            .filter(loc -> loc != null && !loc.equals("unknown"))
            .distinct()
            .count();
    }
    
    private String getStackLocation(QueryInfo query, int depth, boolean includeLineNumber) {
        if (query == null || query.getStackTrace() == null || query.getStackTrace().length <= depth) {
            return "unknown";
        }
        
        StackTraceElement frame = query.getStackTrace()[depth];
        if (frame == null) {
            return "unknown";
        }
        
        String location = frame.getClassName() + "." + frame.getMethodName();
        
        if (includeLineNumber && frame.getLineNumber() > 0) {
            location += ":" + frame.getLineNumber();
        }
        
        return location;
    }
    

    private String getStackClass(QueryInfo query) {
        if (query == null || query.getStackTrace() == null || query.getStackTrace().length == 0) {
            return "unknown";
        }
        
        StackTraceElement frame = query.getStackTrace()[0];
        return frame != null ? frame.getClassName() : "unknown";
    }
    
    private boolean hasLoopPattern(List<QueryInfo> queries) {
        if (queries.size() < 3) {
            return false;
        }
        
        try {
            for (int depth = 2; depth <= 3; depth++) {
                final int currentDepth = depth;
                
                long queriesWithSufficientDepth = queries.stream()
                    .filter(q -> q.getStackTrace() != null && q.getStackTrace().length > currentDepth)
                    .count();
                
                // Use configurable threshold instead of hardcoded 0.8
                double minRatio = config.getLoopDetectionMinQueriesRatio();
                if (queriesWithSufficientDepth < queries.size() * minRatio) {
                    continue;
                }
                
                // Count distinct locations (excluding "unknown")
                long distinctLocations = countDistinctLocations(queries, currentDepth, false);
                
                // If most queries share same deeper frame, it's a loop
                // Must be exactly 1 distinct location AND have valid data
                if (distinctLocations == 1 && queriesWithSufficientDepth > 0) {
                    // Verify immediate frames are different (different loop iterations)
                    long immediateDistinct = countDistinctLocations(queries, 0, false);
                    if (immediateDistinct > 1) {
                        return true; // Same deep frame + different immediate = loop
                    }
                }
            }
        } catch (Exception e) {
            return false;
        }
        
        return false;
    }
}
