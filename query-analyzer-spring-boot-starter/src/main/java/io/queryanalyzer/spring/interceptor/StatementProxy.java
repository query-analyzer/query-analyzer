package io.queryanalyzer.spring.interceptor;

import io.queryanalyzer.core.tracker.QueryTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public final class StatementProxy {

    private static final Logger log = LoggerFactory.getLogger(StatementProxy.class);

    private StatementProxy() {
    }


    public static Statement wrap(Statement statement) {
        return ProxyFactory.createProxy(
            statement,
            Statement.class,
            new StatementInvocationHandler(statement, null)
        );
    }


    public static PreparedStatement wrapPrepared(PreparedStatement statement, String sql) {
        return ProxyFactory.createProxy(
            statement,
            PreparedStatement.class,
            new PreparedStatementInvocationHandler(statement, sql)
        );
    }


    public static CallableStatement wrapCallable(CallableStatement statement, String sql) {
        return ProxyFactory.createProxy(
            statement,
            CallableStatement.class,
            new PreparedStatementInvocationHandler(statement, sql)
        );
    }



    private static class StatementInvocationHandler implements InvocationHandler {

        protected final Statement delegate;
        protected final String preparedSql;
        protected final List<String> batchSqls = new ArrayList<>();

        StatementInvocationHandler(Statement delegate, String preparedSql) {
            this.delegate = delegate;
            this.preparedSql = preparedSql;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();

            try {
                if (methodName.equals("addBatch") && args != null && args.length > 0 && args[0] instanceof String) {
                    batchSqls.add((String) args[0]);
                    return method.invoke(delegate, args);
                }
                
                if (methodName.equals("clearBatch")) {
                    batchSqls.clear();
                    return method.invoke(delegate, args);
                }

                if (isQueryExecutionMethod(methodName)) {
                    return executeWithTracking(method, args, methodName);
                }

                return method.invoke(delegate, args);
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getTargetException();
            }
        }


        protected boolean isQueryExecutionMethod(String methodName) {
            return methodName.equals("execute") ||
                methodName.equals("executeQuery") ||
                methodName.equals("executeUpdate") ||
                methodName.equals("executeLargeUpdate") ||
                methodName.equals("executeBatch") ||
                methodName.equals("executeLargeBatch");
        }


        protected Object executeWithTracking(Method method, Object[] args, String methodName) throws Throwable {
            long startTime = System.nanoTime();
            Object result = null;
            Throwable caught = null;

            try {
                result = method.invoke(delegate, args);
                return result;

            } catch (java.lang.reflect.InvocationTargetException e) {
                caught = e.getTargetException();
                throw caught;

            } catch (Throwable t) {
                caught = t;
                throw t;

            } finally {
                long endTime = System.nanoTime();
                long executionTimeMs = (endTime - startTime) / 1_000_000;

                if (QueryTracker.isTracking()) {
                    recordQueries(methodName, args, executionTimeMs, result, caught);
                }
            }
        }
        

        protected void recordQueries(String methodName, Object[] args, long executionTimeMs, 
                                     Object result, Throwable error) {
            if (methodName.equals("executeBatch") || methodName.equals("executeLargeBatch")) {
                recordBatchExecution(executionTimeMs, result);
            } else {
                String sql = extractSql(methodName, args);
                QueryTracker.recordQuery(sql, executionTimeMs);
            }
        }
        

        protected void recordBatchExecution(long totalTimeMs, Object result) {
            if (batchSqls.isEmpty()) {
                QueryTracker.recordQuery("BATCH (prepared)", totalTimeMs);
                return;
            }
            
            int batchSize = batchSqls.size();
            long timePerQuery = batchSize > 0 ? totalTimeMs / batchSize : totalTimeMs;
            
            for (String sql : batchSqls) {
                QueryTracker.recordQuery(sql, timePerQuery);
            }
            
            batchSqls.clear();
        }


        protected String extractSql(String methodName, Object[] args) {
            if (preparedSql != null) {
                return preparedSql;
            }

            if (args != null && args.length > 0 && args[0] instanceof String) {
                return (String) args[0];
            }

            return "UNKNOWN";
        }
    }
    

    private static class PreparedStatementInvocationHandler extends StatementInvocationHandler {
        
        private final Map<Object, Object> currentParams = new LinkedHashMap<>();
        
        private final List<Map<Object, Object>> batchParams = new ArrayList<>();
        
        PreparedStatementInvocationHandler(Statement delegate, String sql) {
            super(delegate, sql);
        }
        
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            
            try {
                if (isParameterSetter(methodName) && args != null && args.length >= 2) {
                    trackParameter(args[0], args[1]);
                    return method.invoke(delegate, args);
                }
                
                if (methodName.equals("addBatch") && (args == null || args.length == 0)) {
                    batchParams.add(new LinkedHashMap<>(currentParams));
                    currentParams.clear();
                    return method.invoke(delegate, args);
                }
                
                if (methodName.equals("clearParameters")) {
                    currentParams.clear();
                    return method.invoke(delegate, args);
                }
                
                if (methodName.equals("clearBatch")) {
                    batchParams.clear();
                    currentParams.clear();
                    return method.invoke(delegate, args);
                }
                
                return super.invoke(proxy, method, args);
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getTargetException();
            }
        }
        
        private boolean isParameterSetter(String methodName) {
            return methodName.startsWith("set") && !methodName.equals("setFetchSize") 
                && !methodName.equals("setFetchDirection") && !methodName.equals("setMaxRows")
                && !methodName.equals("setMaxFieldSize") && !methodName.equals("setQueryTimeout")
                && !methodName.equals("setEscapeProcessing") && !methodName.equals("setCursorName")
                && !methodName.equals("setPoolable") && !methodName.equals("setLargeMaxRows");
        }
        
        private void trackParameter(Object index, Object value) {
            currentParams.put(index, toSafeValue(value));
        }
        
        private Object toSafeValue(Object value) {
            if (value == null) {
                return "NULL";
            }
            if (value instanceof String) {
                String s = (String) value;
                return s.length() > 100 ? s.substring(0, 97) + "..." : s;
            }
            if (value instanceof byte[]) {
                return "[BLOB " + ((byte[]) value).length + " bytes]";
            }
            if (value instanceof java.io.InputStream || value instanceof java.io.Reader) {
                return "[STREAM]";
            }
            return value.toString();
        }
        
        @Override
        protected void recordQueries(String methodName, Object[] args, long executionTimeMs,
                                     Object result, Throwable error) {
            if (methodName.equals("executeBatch") || methodName.equals("executeLargeBatch")) {
                recordPreparedBatchExecution(executionTimeMs, result);
            } else {
                String sqlWithParams = buildSqlWithParams(preparedSql, currentParams);
                QueryTracker.recordQuery(sqlWithParams, executionTimeMs);
                currentParams.clear();
            }
        }
        
        private void recordPreparedBatchExecution(long totalTimeMs, Object result) {
            int batchSize = batchParams.size();
            if (batchSize == 0) {
                QueryTracker.recordQuery(preparedSql + " [BATCH]", totalTimeMs);
                return;
            }
            
            String batchSql = String.format("%s [BATCH size=%d]", preparedSql, batchSize);
            QueryTracker.recordQuery(batchSql, totalTimeMs);
            
            batchParams.clear();
            currentParams.clear();
        }
        

        private String buildSqlWithParams(String sql, Map<Object, Object> params) {
            if (sql == null || params.isEmpty()) {
                return sql != null ? sql : "UNKNOWN";
            }
            
            // For logging purposes, append params summary
            StringBuilder sb = new StringBuilder(sql);
            sb.append(" /* params: ");
            
            boolean first = true;
            for (Map.Entry<Object, Object> entry : params.entrySet()) {
                if (!first) sb.append(", ");
                first = false;
                sb.append(entry.getKey()).append("=").append(entry.getValue());
            }
            sb.append(" */");
            
            return sb.toString();
        }
    }
}
