package io.queryanalyzer.core.suggestion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FrameworkSuggestionProviderTest {
    
    private FrameworkSuggestionProvider provider;
    
    @BeforeEach
    void setUp() {
        provider = new FrameworkSuggestionProvider();
    }
    
    @Test
    void detectsHibernate() {
        StackTraceElement[] stack = new StackTraceElement[] {
            new StackTraceElement("com.example.Service", "find", "Service.java", 50),
            new StackTraceElement("org.hibernate.internal.SessionImpl", "list", "SessionImpl.java", 100)
        };
        
        assertEquals(FrameworkSuggestionProvider.Framework.HIBERNATE, 
            provider.detectFramework(stack));
    }
    
    @Test
    void detectsMyBatis() {
        StackTraceElement[] stack = new StackTraceElement[] {
            new StackTraceElement("com.example.Mapper", "select", "Mapper.java", 10),
            new StackTraceElement("org.apache.ibatis.session.SqlSession", "selectList", "SqlSession.java", 100)
        };
        
        assertEquals(FrameworkSuggestionProvider.Framework.MYBATIS, 
            provider.detectFramework(stack));
    }
    
    @Test
    void detectsJooq() {
        StackTraceElement[] stack = new StackTraceElement[] {
            new StackTraceElement("com.example.Repo", "find", "Repo.java", 10),
            new StackTraceElement("org.jooq.impl.DSL", "select", "DSL.java", 100)
        };
        
        assertEquals(FrameworkSuggestionProvider.Framework.JOOQ, 
            provider.detectFramework(stack));
    }
    
    @Test
    void detectsSpringJdbc() {
        StackTraceElement[] stack = new StackTraceElement[] {
            new StackTraceElement("com.example.Dao", "find", "Dao.java", 10),
            new StackTraceElement("org.springframework.jdbc.core.JdbcTemplate", "query", "JdbcTemplate.java", 100)
        };
        
        assertEquals(FrameworkSuggestionProvider.Framework.SPRING_JDBC, 
            provider.detectFramework(stack));
    }
    
    @Test
    void returnsUnknownForUnrecognized() {
        StackTraceElement[] stack = new StackTraceElement[] {
            new StackTraceElement("com.example.Service", "find", "Service.java", 10)
        };
        
        assertEquals(FrameworkSuggestionProvider.Framework.UNKNOWN, 
            provider.detectFramework(stack));
    }
    
    @Test
    void returnsUnknownForNull() {
        assertEquals(FrameworkSuggestionProvider.Framework.UNKNOWN, 
            provider.detectFramework(null));
    }
    
    @Test
    void providesHibernateHints() {
        List<String> hints = provider.getSuggestions(
            FrameworkSuggestionProvider.Framework.HIBERNATE, 10);
        
        assertFalse(hints.isEmpty());
        String combined = String.join(" ", hints);
        assertTrue(combined.contains("JOIN FETCH") || combined.contains("BatchSize"));
    }
    
    @Test
    void providesGenericHints() {
        List<String> hints = provider.getSuggestions(
            FrameworkSuggestionProvider.Framework.UNKNOWN, 10);
        
        assertFalse(hints.isEmpty());
    }
}
