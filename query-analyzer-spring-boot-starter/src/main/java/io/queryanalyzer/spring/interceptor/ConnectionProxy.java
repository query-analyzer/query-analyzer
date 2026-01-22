package io.queryanalyzer.spring.interceptor;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

public final class ConnectionProxy {

    private ConnectionProxy() {
    }

    /**
     * Marker interface to identify already-wrapped connections.
     */
    public interface QueryAnalyzerConnectionMarker {
    }

    public static Connection wrap(Connection connection) {
        // Prevent double-wrapping
        if (connection instanceof QueryAnalyzerConnectionMarker) {
            return connection;
        }
        
        return (Connection) ProxyFactory.createProxy(
            connection,
            new Class<?>[]{Connection.class, QueryAnalyzerConnectionMarker.class},
            new ConnectionInvocationHandler(connection)
        );
    }


    private static class ConnectionInvocationHandler implements InvocationHandler {

        private final Connection delegate;

        ConnectionInvocationHandler(Connection delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Object result;
            try {
                result = method.invoke(delegate, args);
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getTargetException();
            }


            if (result instanceof CallableStatement) {
                String sql = extractSqlArgument(args);
                return StatementProxy.wrapCallable((CallableStatement) result, sql);
            } else if (result instanceof PreparedStatement) {
                String sql = extractSqlArgument(args);
                return StatementProxy.wrapPrepared((PreparedStatement) result, sql);
            } else if (result instanceof Statement) {
                return StatementProxy.wrap((Statement) result);
            }

            return result;
        }


        private String extractSqlArgument(Object[] args) {
            if (args != null && args.length > 0 && args[0] instanceof String) {
                return (String) args[0];
            }
            return null;
        }
    }
}
