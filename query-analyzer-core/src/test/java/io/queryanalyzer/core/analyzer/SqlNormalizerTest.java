package io.queryanalyzer.core.analyzer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SqlNormalizerTest {

    @Test
    void shouldNormalizeNumericParameters() {
        String sql = "SELECT * FROM users WHERE id = 123";
        String normalized = SqlNormalizer.normalize(sql);

        assertThat(normalized).isEqualTo("select * from users where id = ?");
    }

    @Test
    void shouldNormalizeStringParameters() {
        String sql = "SELECT * FROM users WHERE name = 'John'";
        String normalized = SqlNormalizer.normalize(sql);

        assertThat(normalized).isEqualTo("select * from users where name = ?");
    }

    @Test
    void shouldNormalizeMultipleParameters() {
        String sql = "SELECT * FROM orders WHERE user_id = 123 AND status = 'active'";
        String normalized = SqlNormalizer.normalize(sql);

        assertThat(normalized).isEqualTo("select * from orders where user_id = ? and status = ?");
    }

    @Test
    void shouldExtractQueryType() {
        assertThat(SqlNormalizer.extractQueryType("SELECT * FROM users")).isEqualTo("SELECT");
        assertThat(SqlNormalizer.extractQueryType("INSERT INTO users VALUES (1)")).isEqualTo("INSERT");
        assertThat(SqlNormalizer.extractQueryType("UPDATE users SET name = 'x'")).isEqualTo("UPDATE");
        assertThat(SqlNormalizer.extractQueryType("DELETE FROM users")).isEqualTo("DELETE");
    }

    @Test
    void shouldExtractAdvancedQueryTypes() {
        // Phase 2: New SQL types
        assertThat(SqlNormalizer.extractQueryType("MERGE INTO target USING source")).isEqualTo("MERGE");
        assertThat(SqlNormalizer.extractQueryType("WITH cte AS (SELECT * FROM users) SELECT * FROM cte")).isEqualTo("WITH");
        assertThat(SqlNormalizer.extractQueryType("CALL my_procedure(123)")).isEqualTo("CALL");
        assertThat(SqlNormalizer.extractQueryType("EXEC sp_executesql N'SELECT 1'")).isEqualTo("EXEC");
        assertThat(SqlNormalizer.extractQueryType("EXECUTE my_stored_proc")).isEqualTo("EXECUTE");
    }

    @Test
    void shouldHandleCaseInsensitiveQueryTypes() {
        assertThat(SqlNormalizer.extractQueryType("merge into target")).isEqualTo("MERGE");
        assertThat(SqlNormalizer.extractQueryType("with cte as")).isEqualTo("WITH");
        assertThat(SqlNormalizer.extractQueryType("call procedure")).isEqualTo("CALL");
        assertThat(SqlNormalizer.extractQueryType("exec stored_proc")).isEqualTo("EXEC");
    }

    @Test
    void shouldHandleEmptyString() {
        assertThat(SqlNormalizer.normalize("")).isEmpty();
        assertThat(SqlNormalizer.normalize(null)).isEmpty();
    }

    @Test
    void shouldHandleSqlEscapedQuotes() {
        String sql = "SELECT * FROM users WHERE name = 'O''Brien'";
        String normalized = SqlNormalizer.normalize(sql);

        assertThat(normalized).isEqualTo("select * from users where name = ?");
    }

    @Test
    void shouldHandleMultipleEscapedQuotes() {
        String sql = "SELECT * FROM books WHERE title = 'John''s Book About Bob''s Life'";
        String normalized = SqlNormalizer.normalize(sql);

        assertThat(normalized).isEqualTo("select * from books where title = ?");
    }

    @Test
    void shouldHandleEmptyStringLiteral() {
        String sql = "SELECT * FROM test WHERE val = ''";
        String normalized = SqlNormalizer.normalize(sql);

        assertThat(normalized).isEqualTo("select * from test where val = ?");
    }

    @Test
    void shouldHandleConsecutiveEscapedQuotes() {
        String sql = "SELECT * FROM test WHERE text = ''''";
        String normalized = SqlNormalizer.normalize(sql);

        assertThat(normalized).isEqualTo("select * from test where text = ?");
    }

    @Test
    void shouldHandleMultipleStringsWithEscapes() {
        String sql = "INSERT INTO users (first, last) VALUES ('O''Brien', 'D''Angelo')";
        String normalized = SqlNormalizer.normalize(sql);

        assertThat(normalized).isEqualTo("insert into users (first, last) values (?, ?)");
    }
    
    // ========== NEW TESTS FOR LIMIT/OFFSET PRESERVATION ==========
    
    @Test
    void shouldPreserveLimitValue() {
        String sql = "SELECT * FROM users LIMIT 100";
        String normalized = SqlNormalizer.normalize(sql);
        
        // LIMIT value should be preserved for pagination detection
        assertThat(normalized).contains("limit 100");
    }
    
    @Test
    void shouldPreserveOffsetValue() {
        String sql = "SELECT * FROM users LIMIT 10 OFFSET 20";
        String normalized = SqlNormalizer.normalize(sql);
        
        // Both LIMIT and OFFSET should be preserved
        assertThat(normalized).contains("limit 10");
        assertThat(normalized).contains("offset 20");
    }
    
    @Test
    void shouldPreserveFetchValue() {
        String sql = "SELECT * FROM users FETCH FIRST 50 ROWS ONLY";
        String normalized = SqlNormalizer.normalize(sql);
        
        assertThat(normalized).contains("fetch first 50");
    }
    
    @Test
    void shouldPreserveTableNamesWithNumbers() {
        String sql = "SELECT * FROM user_v2 WHERE id = 123";
        String normalized = SqlNormalizer.normalize(sql);
        
        assertThat(normalized).contains("user_v2");
        assertThat(normalized).contains("where id = ?");
    }
    
    @Test
    void shouldPreserveColumnNamesWithNumbers() {
        String sql = "SELECT col_1, col_2 FROM test WHERE val = 5";
        String normalized = SqlNormalizer.normalize(sql);
        
        assertThat(normalized).contains("col_1");
        assertThat(normalized).contains("col_2");
    }
    
    @Test
    void shouldExtractLimitValue() {
        assertThat(SqlNormalizer.extractLimit("SELECT * FROM users LIMIT 100")).isEqualTo(100);
        assertThat(SqlNormalizer.extractLimit("SELECT * FROM users LIMIT 50 OFFSET 10")).isEqualTo(50);
        assertThat(SqlNormalizer.extractLimit("SELECT * FROM users")).isNull();
    }
    
    @Test
    void shouldExtractOffsetValue() {
        assertThat(SqlNormalizer.extractOffset("SELECT * FROM users LIMIT 10 OFFSET 20")).isEqualTo(20);
        assertThat(SqlNormalizer.extractOffset("SELECT * FROM users OFFSET 100")).isEqualTo(100);
        assertThat(SqlNormalizer.extractOffset("SELECT * FROM users LIMIT 10")).isNull();
    }
    
    @Test
    void shouldNormalizeWithMetadata() {
        String sql = "SELECT * FROM users WHERE active = 1 LIMIT 25 OFFSET 50";
        SqlNormalizer.NormalizationResult result = SqlNormalizer.normalizeWithMetadata(sql);
        
        assertThat(result.getNormalizedSql()).contains("limit 25");
        assertThat(result.getNormalizedSql()).contains("offset 50");
        assertThat(result.getLimit()).isEqualTo(25);
        assertThat(result.getOffset()).isEqualTo(50);
        assertThat(result.hasPagination()).isTrue();
    }
    
    @Test
    void shouldHandleCaseInsensitiveLimitOffset() {
        String sql = "select * from users limit 10 offset 5";
        SqlNormalizer.NormalizationResult result = SqlNormalizer.normalizeWithMetadata(sql);
        
        assertThat(result.getLimit()).isEqualTo(10);
        assertThat(result.getOffset()).isEqualTo(5);
    }
}
