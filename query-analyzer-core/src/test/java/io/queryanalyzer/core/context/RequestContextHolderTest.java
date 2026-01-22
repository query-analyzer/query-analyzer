package io.queryanalyzer.core.context;

import io.queryanalyzer.core.model.QueryInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class RequestContextHolderTest {

    @BeforeEach
    void setUp() {
        RequestContextHolder.setEnabled(true);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
        RequestContextHolder.setEnabled(true);
    }

    @Test
    void shouldStartAndGetContext() {
        RequestContextHolder.start("/api/users", "GET");

        RequestContext context = RequestContextHolder.get();

        assertThat(context).isNotNull();
        assertThat(context.getEndpoint()).isEqualTo("/api/users");
        assertThat(context.getHttpMethod()).isEqualTo("GET");
        assertThat(context.getRequestId()).isNotNull();
        assertThat(context.getStartTime()).isNotNull();
    }

    @Test
    void shouldStartWithUserId() {
        RequestContextHolder.start("/api/users", "GET", "user-123");

        RequestContext context = RequestContextHolder.get();

        assertThat(context).isNotNull();
        assertThat(context.getUserId()).isEqualTo("user-123");
    }

    @Test
    void shouldStartWithHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer token");

        RequestContextHolder.start("/api/users", "GET", "user-123", headers);

        RequestContext context = RequestContextHolder.get();

        assertThat(context).isNotNull();
        assertThat(context.getHeaders()).containsKey("Authorization");
    }

    @Test
    void shouldClearContext() {
        RequestContextHolder.start("/api/users", "GET");
        assertThat(RequestContextHolder.get()).isNotNull();

        RequestContextHolder.clear();

        assertThat(RequestContextHolder.get()).isNull();
    }

    @Test
    void shouldCheckIfActive() {
        assertThat(RequestContextHolder.isActive()).isFalse();

        RequestContextHolder.start("/api/users", "GET");

        assertThat(RequestContextHolder.isActive()).isTrue();

        RequestContextHolder.clear();

        assertThat(RequestContextHolder.isActive()).isFalse();
    }

    @Test
    void shouldGenerateUniqueRequestIds() {
        RequestContextHolder.start("/api/users", "GET");
        String requestId1 = RequestContextHolder.get().getRequestId();
        RequestContextHolder.clear();

        RequestContextHolder.start("/api/orders", "POST");
        String requestId2 = RequestContextHolder.get().getRequestId();
        RequestContextHolder.clear();

        assertThat(requestId1).isNotEqualTo(requestId2);
        assertThat(requestId1).hasSize(8);
        assertThat(requestId2).hasSize(8);
    }

    @Test
    void shouldIsolateContextBetweenThreads() throws Exception {
        RequestContextHolder.start("/api/users", "GET", "main-user");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<String> future = executor.submit(() -> {
                RequestContext context = RequestContextHolder.get();
                if (context == null) {
                    return null;
                }
                return context.getUserId();
            });

            String otherThreadUserId = future.get();

            RequestContext mainContext = RequestContextHolder.get();

            assertThat(mainContext).isNotNull();
            assertThat(mainContext.getUserId()).isEqualTo("main-user");
            assertThat(otherThreadUserId).isNull();

        } finally {
            executor.shutdown();
        }
    }

    @Test
    void shouldHandleMultipleStartCalls() {
        RequestContextHolder.start("/api/users", "GET");
        RequestContext first = RequestContextHolder.get();

        RequestContextHolder.start("/api/orders", "POST");
        RequestContext second = RequestContextHolder.get();

        assertThat(second.getEndpoint()).isEqualTo("/api/orders");
        assertThat(second.getHttpMethod()).isEqualTo("POST");
        assertThat(first.getRequestId()).isNotEqualTo(second.getRequestId());
    }

    @Test
    void shouldRecordQueries() {
        RequestContextHolder.start("/api/users", "GET");

        QueryInfo query = createTestQuery("SELECT * FROM users", 10);
        RequestContextHolder.recordQuery(query);

        List<QueryInfo> queries = RequestContextHolder.getQueries();

        assertThat(queries).hasSize(1);
        assertThat(queries.get(0).getSql()).isEqualTo("SELECT * FROM users");
    }

    @Test
    void shouldRecordMultipleQueries() {
        RequestContextHolder.start("/api/users", "GET");

        RequestContextHolder.recordQuery(createTestQuery("SELECT * FROM users", 10));
        RequestContextHolder.recordQuery(createTestQuery("SELECT * FROM orders", 20));
        RequestContextHolder.recordQuery(createTestQuery("SELECT * FROM products", 30));

        List<QueryInfo> queries = RequestContextHolder.getQueries();

        assertThat(queries).hasSize(3);
    }

    @Test
    void shouldReturnEmptyQueriesWhenNoContext() {
        List<QueryInfo> queries = RequestContextHolder.getQueries();

        assertThat(queries).isEmpty();
    }

    @Test
    void shouldReturnEmptyQueriesWhenDisabled() {
        RequestContextHolder.start("/api/users", "GET");
        RequestContextHolder.setEnabled(false);

        List<QueryInfo> queries = RequestContextHolder.getQueries();

        assertThat(queries).isEmpty();
    }

    @Test
    void shouldNotStartWhenDisabled() {
        RequestContextHolder.setEnabled(false);
        RequestContextHolder.start("/api/users", "GET");

        RequestContextHolder.setEnabled(true);
        RequestContext context = RequestContextHolder.get();

        assertThat(context).isNull();
    }

    @Test
    void shouldNotRecordQueryWhenDisabled() {
        RequestContextHolder.start("/api/users", "GET");
        RequestContextHolder.setEnabled(false);

        RequestContextHolder.recordQuery(createTestQuery("SELECT * FROM users", 10));

        RequestContextHolder.setEnabled(true);
        List<QueryInfo> queries = RequestContextHolder.getQueries();

        assertThat(queries).isEmpty();
    }

    @Test
    void shouldGetEndpoint() {
        RequestContextHolder.start("/api/users", "GET");

        String endpoint = RequestContextHolder.getEndpoint();

        assertThat(endpoint).isEqualTo("/api/users");
    }

    @Test
    void shouldReturnNullEndpointWhenNoContext() {
        String endpoint = RequestContextHolder.getEndpoint();

        assertThat(endpoint).isNull();
    }

    @Test
    void shouldIgnoreNullQueryRecord() {
        RequestContextHolder.start("/api/users", "GET");

        RequestContextHolder.recordQuery(null);

        List<QueryInfo> queries = RequestContextHolder.getQueries();

        assertThat(queries).isEmpty();
    }

    private QueryInfo createTestQuery(String sql, long executionTimeMs) {
        return new QueryInfo(
            sql,
            sql,
            executionTimeMs,
            Instant.now(),
            null,
            "test-thread",
            null
        );
    }
}
