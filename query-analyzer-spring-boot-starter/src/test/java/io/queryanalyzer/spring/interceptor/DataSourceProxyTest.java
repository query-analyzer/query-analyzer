package io.queryanalyzer.spring.interceptor;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DataSourceProxyTest {

    @Test
    void shouldWrapDataSource() {

        DataSource original = mock(DataSource.class);


        DataSource proxy = DataSourceProxy.wrap(original);


        assertThat(proxy).isNotNull();
        assertThat(ProxyFactory.isProxy(proxy)).isTrue();
    }

    @Test
    void shouldWrapReturnedConnections() throws Exception {

        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConnection = mock(Connection.class);
        when(mockDataSource.getConnection()).thenReturn(mockConnection);

        DataSource proxy = DataSourceProxy.wrap(mockDataSource);


        Connection connection = proxy.getConnection();


        assertThat(connection).isNotNull();
        assertThat(ProxyFactory.isProxy(connection)).isTrue();
    }

    @Test
    void shouldWrapConnectionsWithCredentials() throws Exception {

        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConnection = mock(Connection.class);
        when(mockDataSource.getConnection("user", "pass")).thenReturn(mockConnection);

        DataSource proxy = DataSourceProxy.wrap(mockDataSource);


        Connection connection = proxy.getConnection("user", "pass");


        assertThat(connection).isNotNull();
        assertThat(ProxyFactory.isProxy(connection)).isTrue();
    }

    @Test
    void shouldDelegateOtherMethods() throws Exception {

        DataSource mockDataSource = mock(DataSource.class);
        when(mockDataSource.getLoginTimeout()).thenReturn(30);

        DataSource proxy = DataSourceProxy.wrap(mockDataSource);


        int timeout = proxy.getLoginTimeout();
        proxy.setLoginTimeout(60);


        assertThat(timeout).isEqualTo(30);
        verify(mockDataSource).setLoginTimeout(60);
    }

    @Test
    void shouldNotDoubleWrapDataSource() {
        DataSource mockDataSource = mock(DataSource.class);
        
        DataSource wrapped1 = DataSourceProxy.wrap(mockDataSource);
        
        DataSource wrapped2 = DataSourceProxy.wrap(wrapped1);
        
        assertThat(wrapped2).isSameAs(wrapped1);
    }

    @Test
    void shouldImplementMarkerInterface() {
        DataSource mockDataSource = mock(DataSource.class);
        DataSource wrapped = DataSourceProxy.wrap(mockDataSource);
        
        assertThat(wrapped).isInstanceOf(DataSourceProxy.QueryAnalyzerMarker.class);
    }

    @Test
    void shouldReturnSameDataSourceIfAlreadyMarked() {
        DataSource mockDataSource = mock(DataSource.class);
        
        DataSource wrapped = DataSourceProxy.wrap(mockDataSource);
        
        DataSource wrappedAgain = DataSourceProxy.wrap(wrapped);
        
        assertThat(wrappedAgain).isSameAs(wrapped);
    }
}
