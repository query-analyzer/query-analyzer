package io.queryanalyzer.core.context;

import io.queryanalyzer.core.model.QueryInfo;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RequestContextTest {

    @Test
    void shouldCreateBasicContext() {
        RequestContext context = new RequestContext(
            "test-123", 
            "/api/users", 
            "GET", 
            null, 
            null
        );

        assertThat(context.getRequestId()).isEqualTo("test-123");
        assertThat(context.getEndpoint()).isEqualTo("/api/users");
        assertThat(context.getHttpMethod()).isEqualTo("GET");
        assertThat(context.getStartTime()).isNotNull();
    }

    @Test
    void shouldHandleOptionalFields() {
        RequestContext context = new RequestContext(
            "test-123",
            "/api/users",
            "GET",
            "user-456",
            null
        );

        assertThat(context.getUserId()).isEqualTo("user-456");
    }

    @Test
    void shouldHandleHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer token");
        headers.put("Content-Type", "application/json");

        RequestContext context = new RequestContext(
            "test-123",
            "/api/users",
            "POST",
            null,
            headers
        );

        assertThat(context.getHeaders()).containsKeys("Authorization", "Content-Type");
        assertThat(context.getHeaders().get("Authorization")).isEqualTo("Bearer token");
    }

    @Test
    void shouldInitializeEmptyQueriesList() {
        RequestContext context = new RequestContext(
            "test-123",
            "/api/users",
            "GET",
            null,
            null
        );

        assertThat(context.getQueries()).isNotNull();
        assertThat(context.getQueries()).isEmpty();
        assertThat(context.hasQueries()).isFalse();
        assertThat(context.getQueryCount()).isZero();
    }

    @Test
    void shouldRecordQueries() {
        RequestContext context = new RequestContext(
            "test-123",
            "/api/users",
            "GET",
            null,
            null
        );

        QueryInfo query1 = createTestQuery("SELECT * FROM users", 10);
        QueryInfo query2 = createTestQuery("SELECT * FROM orders", 20);

        context.recordQuery(query1);
        context.recordQuery(query2);

        assertThat(context.getQueryCount()).isEqualTo(2);
        assertThat(context.hasQueries()).isTrue();
        assertThat(context.getQueries()).containsExactly(query1, query2);
    }

    @Test
    void shouldCalculateTotalQueryTime() {
        RequestContext context = new RequestContext(
            "test-123",
            "/api/users",
            "GET",
            null,
            null
        );

        context.recordQuery(createTestQuery("SELECT * FROM users", 10));
        context.recordQuery(createTestQuery("SELECT * FROM orders", 20));
        context.recordQuery(createTestQuery("SELECT * FROM products", 30));

        assertThat(context.getTotalQueryTimeMs()).isEqualTo(60);
    }

    @Test
    void shouldClearQueries() {
        RequestContext context = new RequestContext(
            "test-123",
            "/api/users",
            "GET",
            null,
            null
        );

        context.recordQuery(createTestQuery("SELECT * FROM users", 10));
        context.recordQuery(createTestQuery("SELECT * FROM orders", 20));
        
        assertThat(context.getQueryCount()).isEqualTo(2);
        
        context.clear();
        
        assertThat(context.getQueryCount()).isZero();
        assertThat(context.hasQueries()).isFalse();
    }

    private QueryInfo createTestQuery(String sql, long executionTimeMs) {
        return new QueryInfo(
            sql,
            sql, // normalized
            executionTimeMs,
            Instant.now(),
            null, // stackTrace
            "test-thread",
            null // metadata
        );
    }

    @Test
    void shouldCalculateDuration() throws InterruptedException {
        RequestContext context = new RequestContext(
            "test-123",
            "/api/users",
            "GET",
            null,
            null
        );

        Thread.sleep(100); // Wait 100ms

        Duration duration = context.getDuration();

        assertThat(duration.toMillis()).isGreaterThanOrEqualTo(100);
    }

    @Test
    void shouldDetectStaleContext() throws InterruptedException {
        // Can't easily test 5-minute staleness, but we can verify the method exists
        RequestContext context = new RequestContext(
            "test-123",
            "/api/users",
            "GET",
            null,
            null
        );

        // Fresh context should not be stale
        assertThat(context.isStale()).isFalse();
    }

    @Test
    void shouldHandleNullUserId() {
        RequestContext context = new RequestContext(
            "test-123",
            "/api/users",
            "GET",
            null,
            null
        );

        assertThat(context.getUserId()).isNull();
    }

    @Test
    void shouldHandleNullHeaders() {
        RequestContext context = new RequestContext(
            "test-123",
            "/api/users",
            "GET",
            null,
            null
        );

        assertThat(context.getHeaders()).isNull();
    }

    @Test
    void shouldReturnUnmodifiableQueriesList() {
        RequestContext context = new RequestContext(
            "test-123",
            "/api/users",
            "GET",
            null,
            null
        );

        context.recordQuery(createTestQuery("SELECT * FROM users", 10));

        // Should throw when trying to modify
        org.junit.jupiter.api.Assertions.assertThrows(
            UnsupportedOperationException.class,
            () -> context.getQueries().add(createTestQuery("SELECT 1", 1))
        );
    }

    @Test
    void shouldHaveReadableToString() {
        RequestContext context = new RequestContext(
            "test-123",
            "/api/users",
            "GET",
            null,
            null
        );

        context.recordQuery(createTestQuery("SELECT * FROM users", 10));

        String str = context.toString();
        
        assertThat(str).contains("test-123");
        assertThat(str).contains("/api/users");
        assertThat(str).contains("GET");
        assertThat(str).contains("queries=1");
    }
}
