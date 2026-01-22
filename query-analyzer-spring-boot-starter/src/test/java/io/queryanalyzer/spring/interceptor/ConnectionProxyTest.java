package io.queryanalyzer.spring.interceptor;

import org.junit.jupiter.api.Test;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ConnectionProxyTest {

    @Test
    void shouldWrapCreatedStatements() throws Exception {

        Connection mockConnection = mock(Connection.class);
        Statement mockStatement = mock(Statement.class);
        when(mockConnection.createStatement()).thenReturn(mockStatement);

        Connection proxy = ConnectionProxy.wrap(mockConnection);


        Statement statement = proxy.createStatement();


        assertThat(statement).isNotNull();
        assertThat(ProxyFactory.isProxy(statement)).isTrue();
    }

    @Test
    void shouldWrapPreparedStatements() throws Exception {

        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockPrepared = mock(PreparedStatement.class);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPrepared);

        Connection proxy = ConnectionProxy.wrap(mockConnection);


        PreparedStatement statement = proxy.prepareStatement("SELECT * FROM users");


        assertThat(statement).isNotNull();
        assertThat(ProxyFactory.isProxy(statement)).isTrue();
    }

    @Test
    void shouldDelegateNonStatementMethods() throws Exception {

        Connection mockConnection = mock(Connection.class);
        Connection proxy = ConnectionProxy.wrap(mockConnection);


        proxy.setAutoCommit(false);
        proxy.commit();
        proxy.close();


        verify(mockConnection).setAutoCommit(false);
        verify(mockConnection).commit();
        verify(mockConnection).close();
    }

    @Test
    void shouldWrapCallableStatements() throws Exception {
        Connection mockConnection = mock(Connection.class);
        CallableStatement mockCallable = mock(CallableStatement.class);
        when(mockConnection.prepareCall(anyString())).thenReturn(mockCallable);

        Connection proxy = ConnectionProxy.wrap(mockConnection);

        CallableStatement statement = proxy.prepareCall("CALL my_procedure()");

        assertThat(statement).isNotNull();
        assertThat(ProxyFactory.isProxy(statement)).isTrue();
    }

    @Test
    void shouldNotDoubleWrapCallableStatement() throws Exception {
        Connection mockConnection = mock(Connection.class);
        CallableStatement mockCallable = mock(CallableStatement.class);
        when(mockConnection.prepareCall(anyString())).thenReturn(mockCallable);

        Connection proxy = ConnectionProxy.wrap(mockConnection);
        CallableStatement statement = proxy.prepareCall("CALL proc()");

        assertThat(statement).isInstanceOf(CallableStatement.class);
        assertThat(ProxyFactory.isProxy(statement)).isTrue();
        
        verify(mockConnection).prepareCall("CALL proc()");
    }
}
