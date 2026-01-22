package io.queryanalyzer.spring.interceptor;

import io.queryanalyzer.core.tracker.QueryTracker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class StatementProxyTest {

    @BeforeEach
    void setUp() {
        QueryTracker.startTracking();
    }

    @AfterEach
    void tearDown() {
        QueryTracker.clear();
    }

    @Test
    void shouldTrackRegularStatementQuery() throws SQLException {

        Statement mockStatement = mock(Statement.class);
        ResultSet mockResult = mock(ResultSet.class);
        when(mockStatement.executeQuery("SELECT * FROM users")).thenReturn(mockResult);

        Statement proxy = StatementProxy.wrap(mockStatement);


        proxy.executeQuery("SELECT * FROM users");


        assertThat(QueryTracker.getQueries()).hasSize(1);
        assertThat(QueryTracker.getQueries().get(0).getSql()).isEqualTo("SELECT * FROM users");
    }

    @Test
    void shouldTrackPreparedStatementExecution() throws SQLException {

        PreparedStatement mockStatement = mock(PreparedStatement.class);
        ResultSet mockResult = mock(ResultSet.class);
        when(mockStatement.executeQuery()).thenReturn(mockResult);

        PreparedStatement proxy = StatementProxy.wrapPrepared(mockStatement, "SELECT * FROM orders WHERE id = ?");


        proxy.executeQuery();


        assertThat(QueryTracker.getQueries()).hasSize(1);
        assertThat(QueryTracker.getQueries().get(0).getSql()).isEqualTo("SELECT * FROM orders WHERE id = ?");
    }

    @Test
    void shouldTrackExecuteUpdate() throws SQLException {

        Statement mockStatement = mock(Statement.class);
        when(mockStatement.executeUpdate("UPDATE users SET name = 'John'")).thenReturn(1);

        Statement proxy = StatementProxy.wrap(mockStatement);


        proxy.executeUpdate("UPDATE users SET name = 'John'");


        assertThat(QueryTracker.getQueries()).hasSize(1);
        assertThat(QueryTracker.getQueries().get(0).getSql()).contains("UPDATE users");
    }

    @Test
    void shouldTrackExecutionTime() throws SQLException {

        Statement mockStatement = mock(Statement.class);
        when(mockStatement.executeQuery("SELECT * FROM users")).thenAnswer(invocation -> {
            Thread.sleep(50); // Simulate slow query
            return mock(ResultSet.class);
        });

        Statement proxy = StatementProxy.wrap(mockStatement);


        proxy.executeQuery("SELECT * FROM users");


        assertThat(QueryTracker.getQueries()).hasSize(1);
        assertThat(QueryTracker.getQueries().get(0).getExecutionTimeMs()).isGreaterThanOrEqualTo(50);
    }

    @Test
    void shouldNotTrackWhenTrackingDisabled() throws SQLException {

        QueryTracker.clear();
        Statement mockStatement = mock(Statement.class);
        when(mockStatement.executeQuery("SELECT * FROM users")).thenReturn(mock(ResultSet.class));

        Statement proxy = StatementProxy.wrap(mockStatement);


        proxy.executeQuery("SELECT * FROM users");

        verify(mockStatement).executeQuery("SELECT * FROM users");
    }

    @Test
    void shouldDelegateNonQueryMethods() throws SQLException {

        Statement mockStatement = mock(Statement.class);
        Statement proxy = StatementProxy.wrap(mockStatement);


        proxy.setFetchSize(100);
        proxy.close();


        verify(mockStatement).setFetchSize(100);
        verify(mockStatement).close();
    }
}
