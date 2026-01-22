package io.queryanalyzer.core.tracker;

import io.queryanalyzer.core.context.RequestContextHolder;
import io.queryanalyzer.core.model.QueryInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QueryTrackerTest {

    @BeforeEach
    void setup() {
        QueryTracker.setEnabled(true);
    }

    @AfterEach
    void cleanup() {
        QueryTracker.clear();
        QueryTracker.setEnabled(true);
    }

    @Test
    void shouldTrackQueries() {
        // Start via RequestContextHolder (modern API)
        RequestContextHolder.start("/api/users", "GET");

        QueryTracker.recordQuery("SELECT * FROM users WHERE id = 1", 10);
        QueryTracker.recordQuery("SELECT * FROM orders WHERE user_id = 1", 20);

        List<QueryInfo> queries = QueryTracker.getQueries();
        assertThat(queries).hasSize(2);
        assertThat(queries.get(0).getExecutionTimeMs()).isEqualTo(10);
        assertThat(queries.get(1).getExecutionTimeMs()).isEqualTo(20);
    }

    @Test
    void shouldClearQueries() {
        RequestContextHolder.start("/api/users", "GET");
        QueryTracker.recordQuery("SELECT 1", 5);

        QueryTracker.clear();

        List<QueryInfo> queries = QueryTracker.getQueries();
        assertThat(queries).isEmpty();
    }

    @Test
    void shouldNotTrackWhenDisabled() {
        QueryTracker.setEnabled(false);
        RequestContextHolder.start("/api/users", "GET");

        QueryTracker.recordQuery("SELECT 1", 5);

        QueryTracker.setEnabled(true);
        List<QueryInfo> queries = QueryTracker.getQueries();
        assertThat(queries).isEmpty();
    }

    @Test
    void shouldGetEndpoint() {
        RequestContextHolder.start("/api/users", "GET");

        String endpoint = QueryTracker.getEndpoint();

        assertThat(endpoint).isEqualTo("/api/users");
    }

    @Test
    void shouldReturnNullEndpointWhenNoContext() {
        String endpoint = QueryTracker.getEndpoint();

        assertThat(endpoint).isNull();
    }

    @Test
    void shouldReportTrackingStatus() {
        assertThat(QueryTracker.isTracking()).isFalse();

        RequestContextHolder.start("/api/users", "GET");

        assertThat(QueryTracker.isTracking()).isTrue();

        QueryTracker.clear();

        assertThat(QueryTracker.isTracking()).isFalse();
    }

    @Test
    void shouldReportEnabledStatus() {
        assertThat(QueryTracker.isEnabled()).isTrue();

        QueryTracker.setEnabled(false);

        assertThat(QueryTracker.isEnabled()).isFalse();

        QueryTracker.setEnabled(true);

        assertThat(QueryTracker.isEnabled()).isTrue();
    }

    @Test
    @SuppressWarnings("deprecation")
    void legacyStartTrackingShouldCreateMinimalContext() {
        QueryTracker.startTracking();

        QueryTracker.recordQuery("SELECT 1", 5);

        List<QueryInfo> queries = QueryTracker.getQueries();
        assertThat(queries).hasSize(1);
    }
}
