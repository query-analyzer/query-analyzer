package io.queryanalyzer.core.suggestion;

import io.queryanalyzer.core.analyzer.InferredRelationship;

import java.util.ArrayList;
import java.util.List;


public class FrameworkSuggestionProvider {
    
    public enum Framework {
        HIBERNATE("Hibernate/JPA"),
        MYBATIS("MyBatis"),
        SPRING_JDBC("Spring JDBC"),
        JOOQ("jOOQ"),
        UNKNOWN("Unknown");
        
        private final String displayName;
        
        Framework(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    private static final String[] HIBERNATE_MARKERS = {
        "org.hibernate.",
        "jakarta.persistence.",
        "javax.persistence.",
        "org.springframework.orm.jpa.",
        "org.springframework.data.jpa."
    };
    
    private static final String[] MYBATIS_MARKERS = {
        "org.apache.ibatis.",
        "org.mybatis."
    };
    
    private static final String[] SPRING_JDBC_MARKERS = {
        "org.springframework.jdbc.",
        "org.springframework.dao."
    };
    
    private static final String[] JOOQ_MARKERS = {
        "org.jooq."
    };
    

    public Framework detectFramework(StackTraceElement[] stackTrace) {
        if (stackTrace == null || stackTrace.length == 0) {
            return Framework.UNKNOWN;
        }
        
        // Scan entire stack trace to collect all detected frameworks
        // This prevents incorrect detection when multiple frameworks appear
        boolean hasHibernate = false;
        boolean hasMyBatis = false;
        boolean hasJooq = false;
        boolean hasSpringJdbc = false;
        
        for (StackTraceElement element : stackTrace) {
            String className = element.getClassName();
            
            if (matchesAny(className, HIBERNATE_MARKERS)) {
                hasHibernate = true;
            }
            if (matchesAny(className, MYBATIS_MARKERS)) {
                hasMyBatis = true;
            }
            if (matchesAny(className, JOOQ_MARKERS)) {
                hasJooq = true;
            }
            if (matchesAny(className, SPRING_JDBC_MARKERS)) {
                hasSpringJdbc = true;
            }
        }
        
        // Priority: Hibernate > MyBatis > jOOQ > Spring JDBC
        // Hibernate takes priority because Spring Data JPA uses JDBC underneath
        if (hasHibernate) {
            return Framework.HIBERNATE;
        }
        if (hasMyBatis) {
            return Framework.MYBATIS;
        }
        if (hasJooq) {
            return Framework.JOOQ;
        }
        if (hasSpringJdbc) {
            return Framework.SPRING_JDBC;
        }
        
        return Framework.UNKNOWN;
    }
    
    private boolean matchesAny(String className, String[] markers) {
        for (String marker : markers) {
            if (className.startsWith(marker)) {
                return true;
            }
        }
        return false;
    }
    

    public List<String> getSuggestions(Framework framework, 
                                        InferredRelationship relationship,
                                        int queryCount) {
        List<String> hints = new ArrayList<>();
        
        switch (framework) {
            case HIBERNATE -> addHibernateHints(hints, relationship);
            case MYBATIS -> addMyBatisHints(hints);
            case SPRING_JDBC -> addSpringJdbcHints(hints);
            case JOOQ -> addJooqHints(hints);
            default -> addGenericHints(hints);
        }
        
        return hints;
    }
    
    public List<String> getSuggestions(Framework framework, int queryCount) {
        return getSuggestions(framework, null, queryCount);
    }
    
    private void addHibernateHints(List<String> hints, InferredRelationship rel) {
        hints.add("Hibernate detected. Common fixes:");
        hints.add("Use JOIN FETCH in JPQL to load association eagerly");
        hints.add("Add @BatchSize(size=25) to the collection mapping");
        hints.add("Use @EntityGraph to specify fetch plan");
    }
    
    private void addMyBatisHints(List<String> hints) {
        hints.add("MyBatis detected. Common fixes:");
        hints.add("Replace nested select with nested result mapping");
        hints.add("Use JOIN query instead of separate selects");
    }
    
    private void addSpringJdbcHints(List<String> hints) {
        hints.add("Spring JDBC detected. Common fixes:");
        hints.add("Collect IDs and use IN clause for batch lookup");
        hints.add("Rewrite as JOIN query");
    }
    
    private void addJooqHints(List<String> hints) {
        hints.add("jOOQ detected. Common fixes:");
        hints.add("Use multiset() for nested collections");
        hints.add("Use LEFT JOIN to fetch in single query");
    }
    
    private void addGenericHints(List<String> hints) {
        hints.add("Common N+1 fixes:");
        hints.add("Batch load: collect IDs, fetch with IN clause");
        hints.add("JOIN query: fetch parent and children together");
    }
}
