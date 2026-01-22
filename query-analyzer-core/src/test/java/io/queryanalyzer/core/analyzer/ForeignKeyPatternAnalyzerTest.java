package io.queryanalyzer.core.analyzer;

import io.queryanalyzer.core.model.QueryInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;


@DisplayName("ForeignKeyPatternAnalyzer")
class ForeignKeyPatternAnalyzerTest {
    
    private ForeignKeyPatternAnalyzer analyzer;
    
    @BeforeEach
    void setUp() {
        analyzer = new ForeignKeyPatternAnalyzer();
    }
    

    @Test
    @DisplayName("should detect snake_case foreign key pattern (user_id)")
    void detectSnakeCaseForeignKey() {
        String sql = "SELECT * FROM orders WHERE user_id = ?";
        
        Optional<InferredRelationship> result = analyzer.inferFromSql(sql);
        
        assertTrue(result.isPresent());
        assertEquals("orders", result.get().getChildTable());
        assertEquals("users", result.get().getParentTable());
        assertEquals("user_id", result.get().getForeignKeyColumn());
        assertEquals(InferredRelationship.RelationshipType.MANY_TO_ONE, 
            result.get().getRelationshipType());
    }
    
    @Test
    @DisplayName("should detect camelCase foreign key pattern (userId)")
    void detectCamelCaseForeignKey() {
        String sql = "SELECT * FROM orders WHERE userId = ?";
        
        Optional<InferredRelationship> result = analyzer.inferFromSql(sql);
        
        assertTrue(result.isPresent());
        assertEquals("orders", result.get().getChildTable());
        assertEquals("user", result.get().getParentTable());
        assertEquals("userId", result.get().getForeignKeyColumn());
    }
    
    @Test
    @DisplayName("should detect fk_ prefix pattern (fk_author)")
    void detectFkPrefixPattern() {
        String sql = "SELECT * FROM books WHERE fk_author = ?";
        
        Optional<InferredRelationship> result = analyzer.inferFromSql(sql);
        
        assertTrue(result.isPresent());
        assertEquals("books", result.get().getChildTable());
        assertEquals("author", result.get().getParentTable());
        assertEquals("fk_author", result.get().getForeignKeyColumn());
    }
    

    @Test
    @DisplayName("should handle quoted table names")
    void handleQuotedTableNames() {
        String sql = "SELECT * FROM \"orders\" WHERE user_id = ?";
        
        Optional<InferredRelationship> result = analyzer.inferFromSql(sql);
        
        assertTrue(result.isPresent());
        assertEquals("orders", result.get().getChildTable());
    }
    
    @Test
    @DisplayName("should handle schema-qualified table names")
    void handleSchemaQualifiedTables() {
        String sql = "SELECT * FROM public.orders WHERE user_id = ?";
        
        Optional<InferredRelationship> result = analyzer.inferFromSql(sql);
        
        assertTrue(result.isPresent());
        // Should extract just "orders" from "public.orders"
        assertNotNull(result.get().getChildTable());
    }
    

    @Test
    @DisplayName("should pluralize regular nouns (user -> users)")
    void pluralizeRegularNouns() {
        String sql = "SELECT * FROM posts WHERE user_id = ?";
        
        Optional<InferredRelationship> result = analyzer.inferFromSql(sql);
        
        assertTrue(result.isPresent());
        assertEquals("users", result.get().getParentTable());
    }
    
    @Test
    @DisplayName("should pluralize -y ending nouns (category -> categories)")
    void pluralizeCategoryNouns() {
        String sql = "SELECT * FROM products WHERE category_id = ?";
        
        Optional<InferredRelationship> result = analyzer.inferFromSql(sql);
        
        assertTrue(result.isPresent());
        assertEquals("categories", result.get().getParentTable());
    }
    

    @Test
    @DisplayName("should return empty for non-SELECT queries")
    void returnEmptyForNonSelect() {
        String sql = "INSERT INTO orders (user_id) VALUES (?)";
        
        Optional<InferredRelationship> result = analyzer.inferFromSql(sql);
        
        assertFalse(result.isPresent());
    }
    
    @Test
    @DisplayName("should return empty for queries without WHERE clause")
    void returnEmptyWithoutWhere() {
        String sql = "SELECT * FROM orders";
        
        Optional<InferredRelationship> result = analyzer.inferFromSql(sql);
        
        assertFalse(result.isPresent());
    }
    
    @Test
    @DisplayName("should return empty for null SQL")
    void returnEmptyForNull() {
        Optional<InferredRelationship> result = analyzer.inferFromSql(null);
        assertFalse(result.isPresent());
    }
    
    @Test
    @DisplayName("should return empty for empty SQL")
    void returnEmptyForEmpty() {
        Optional<InferredRelationship> result = analyzer.inferFromSql("");
        assertFalse(result.isPresent());
    }
    

    @Test
    @DisplayName("should have high confidence for standard naming (user_id -> users)")
    void highConfidenceForStandardNaming() {
        String sql = "SELECT * FROM orders WHERE user_id = ?";
        
        Optional<InferredRelationship> result = analyzer.inferFromSql(sql);
        
        assertTrue(result.isPresent());
        assertTrue(result.get().getConfidence() >= 0.9, 
            "Expected high confidence for standard naming");
    }
    
    @Test
    @DisplayName("should have lower confidence for generic FK names")
    void lowerConfidenceForGenericNames() {
        String sql = "SELECT * FROM orders WHERE parent_id = ?";
        
        Optional<InferredRelationship> result = analyzer.inferFromSql(sql);
        
        // parent_id is too generic to confidently infer parent table
        // Analyzer may or may not return a result
        if (result.isPresent()) {
            assertTrue(result.get().getConfidence() <= 0.8,
                "Expected lower confidence for generic naming");
        }
    }
    

    @Test
    @DisplayName("should infer relationship from query list")
    void inferFromQueryList() {
        List<QueryInfo> queries = Arrays.asList(
            createQueryInfo("SELECT * FROM orders WHERE user_id = ?"),
            createQueryInfo("SELECT * FROM orders WHERE user_id = ?"),
            createQueryInfo("SELECT * FROM orders WHERE user_id = ?")
        );
        
        Optional<InferredRelationship> result = analyzer.inferRelationship(queries);
        
        assertTrue(result.isPresent());
        assertEquals("orders", result.get().getChildTable());
        assertEquals("users", result.get().getParentTable());
    }
    
    @Test
    @DisplayName("should return empty for empty query list")
    void returnEmptyForEmptyList() {
        Optional<InferredRelationship> result = analyzer.inferRelationship(Collections.emptyList());
        assertFalse(result.isPresent());
    }
    
    @Test
    @DisplayName("should return empty for null query list")
    void returnEmptyForNullList() {
        Optional<InferredRelationship> result = analyzer.inferRelationship(null);
        assertFalse(result.isPresent());
    }
    

    private QueryInfo createQueryInfo(String sql) {
        return QueryInfo.builder()
            .sql(sql)
            .normalizedSql(sql.toLowerCase())
            .executionTimeMs(10)
            .timestamp(Instant.now())
            .build();
    }
}
