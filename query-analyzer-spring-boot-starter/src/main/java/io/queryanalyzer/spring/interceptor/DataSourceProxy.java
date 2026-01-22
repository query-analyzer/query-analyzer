package io.queryanalyzer.spring.interceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.sql.Connection;

public final class DataSourceProxy {

    private static final Logger log = LoggerFactory.getLogger(DataSourceProxy.class);

    private DataSourceProxy() {
    }


    public interface QueryAnalyzerMarker {
    }

    public static DataSource wrap(DataSource dataSource) {
        if (dataSource instanceof QueryAnalyzerMarker) {
            log.debug("DataSource already wrapped by Query Analyzer, skipping double-wrap");
            return dataSource;
        }

        log.debug("Creating DataSource proxy for query tracking");

        return (DataSource) ProxyFactory.createProxy(
            dataSource,
            new Class<?>[]{DataSource.class, QueryAnalyzerMarker.class},
            new DataSourceInvocationHandler(dataSource)
        );
    }


    private static class DataSourceInvocationHandler implements InvocationHandler {

        private final DataSource delegate;

        DataSourceInvocationHandler(DataSource delegate) {
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

            if (result instanceof Connection && method.getName().startsWith("getConnection")) {
                return ConnectionProxy.wrap((Connection) result);
            }

            return result;
        }
    }
}
