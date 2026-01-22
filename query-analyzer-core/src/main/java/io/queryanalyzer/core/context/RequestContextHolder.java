package io.queryanalyzer.core.context;

import io.queryanalyzer.core.model.QueryInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;


public final class RequestContextHolder {
    
    private static final Logger log = LoggerFactory.getLogger(RequestContextHolder.class);
    
    /**
     * Contexts older than this are considered stale and will be cleared.
     * This helps detect ThreadLocal leaks.
     */
    static final int STALE_THRESHOLD_MINUTES = 5;
    
    private static final ThreadLocal<RequestContext> context = new ThreadLocal<>();
    
    private static volatile boolean globalEnabled = true;
    

    private RequestContextHolder() {
        throw new UnsupportedOperationException("Utility class");
    }
    

    public static void start(String endpoint, String httpMethod) {
        start(endpoint, httpMethod, null, null);
    }

    public static void start(String endpoint, String httpMethod, String userId) {
        start(endpoint, httpMethod, userId, null);
    }

    public static void start(String endpoint, String httpMethod, String userId, Map<String, String> headers) {
        if (!globalEnabled) {
            return;
        }
        
        RequestContext existing = context.get();
        if (existing != null) {
            log.warn("Starting new context but previous context was not cleared! " +
                     "Previous: {} {} ({} queries, {}ms old). This indicates a bug - " +
                     "ensure clear() is called in a finally block.",
                     existing.getHttpMethod(), existing.getEndpoint(),
                     existing.getQueryCount(), existing.getDuration().toMillis());
        }
        
        String requestId = generateRequestId();
        RequestContext ctx = new RequestContext(requestId, endpoint, httpMethod, userId, headers);
        context.set(ctx);
        
        log.trace("Started request context: {} {} {}", requestId, httpMethod, endpoint);
    }
    
    public static RequestContext get() {
        if (!globalEnabled) {
            return null;
        }
        
        RequestContext ctx = context.get();
        
        if (ctx != null && ctx.isStale()) {
            log.warn("Stale request context detected (age: {} min), clearing. " +
                     "This indicates a ThreadLocal leak - ensure clear() is always called.",
                     ctx.getDuration().toMinutes());
            clear();
            return null;
        }
        
        return ctx;
    }
    
    public static void clear() {
        RequestContext ctx = context.get();
        if (ctx != null && log.isTraceEnabled()) {
            log.trace("Cleared request context: {} {} ({} queries, {}ms)",
                ctx.getHttpMethod(), 
                ctx.getEndpoint(),
                ctx.getQueryCount(),
                ctx.getDuration().toMillis());
        }
        context.remove();
    }

    public static void recordQuery(QueryInfo query) {
        if (!globalEnabled) {
            return;
        }
        
        RequestContext ctx = context.get();
        if (ctx == null) {
            log.trace("Query recorded but no active context - query will be lost");
            return;
        }
        
        ctx.recordQuery(query);
        
        if (log.isTraceEnabled()) {
            String sqlPreview = query.getNormalizedSql();
            if (sqlPreview.length() > 50) {
                sqlPreview = sqlPreview.substring(0, 50) + "...";
            }
            log.trace("Recorded query: {} ({}ms)", sqlPreview, query.getExecutionTimeMs());
        }
    }

    public static List<QueryInfo> getQueries() {
        if (!globalEnabled) {
            return Collections.emptyList();
        }
        
        RequestContext ctx = context.get();
        return ctx != null ? ctx.getQueries() : Collections.emptyList();
    }
    
    public static boolean isActive() {
        return globalEnabled && context.get() != null;
    }

    public static boolean isEnabled() {
        return globalEnabled;
    }
    
    public static void setEnabled(boolean enabled) {
        globalEnabled = enabled;
        log.debug("Request tracking {}", enabled ? "enabled" : "disabled");
    }
    
    public static String getEndpoint() {
        RequestContext ctx = get();
        return ctx != null ? ctx.getEndpoint() : null;
    }
    
    public static String getHttpMethod() {
        RequestContext ctx = get();
        return ctx != null ? ctx.getHttpMethod() : null;
    }

    private static String generateRequestId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
