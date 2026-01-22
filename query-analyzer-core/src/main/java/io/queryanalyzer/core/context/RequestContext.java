package io.queryanalyzer.core.context;

import io.queryanalyzer.core.model.QueryInfo;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public final class RequestContext {
    
    private final String requestId;
    private final String endpoint;
    private final String httpMethod;
    private final String userId;
    private final Map<String, String> headers;
    private final Instant startTime;
    private final List<QueryInfo> queries;
    

    RequestContext(String requestId, String endpoint, String httpMethod, 
                   String userId, Map<String, String> headers) {
        this.requestId = requestId;
        this.endpoint = endpoint;
        this.httpMethod = httpMethod;
        this.userId = userId;
        this.headers = headers != null ? new HashMap<>(headers) : null;
        this.startTime = Instant.now();
        this.queries = Collections.synchronizedList(new ArrayList<>());
    }
    

    public void recordQuery(QueryInfo query) {
        if (query != null) {
            queries.add(query);
        }
    }

    public List<QueryInfo> getQueries() {
        // Return a snapshot copy for safe iteration
        synchronized (queries) {
            return Collections.unmodifiableList(new ArrayList<>(queries));
        }
    }
    

    public String getRequestId() {
        return requestId;
    }
    

    public String getEndpoint() {
        return endpoint;
    }
    

    public String getHttpMethod() {
        return httpMethod;
    }
    

    public String getUserId() {
        return userId;
    }

    public Map<String, String> getHeaders() {
        return headers != null ? Collections.unmodifiableMap(headers) : null;
    }

    public Instant getStartTime() {
        return startTime;
    }
    

    public long getTotalQueryTimeMs() {
        return queries.stream()
            .mapToLong(QueryInfo::getExecutionTimeMs)
            .sum();
    }
    
    public Duration getDuration() {
        return Duration.between(startTime, Instant.now());
    }
    

    public int getQueryCount() {
        return queries.size();
    }
    
    public boolean hasQueries() {
        return !queries.isEmpty();
    }
    
    public boolean isStale() {
        return getDuration().toMinutes() > RequestContextHolder.STALE_THRESHOLD_MINUTES;
    }
    
    void clear() {
        queries.clear();
    }
    
    @Override
    public String toString() {
        return String.format("RequestContext{id=%s, endpoint=%s %s, queries=%d, duration=%dms}",
            requestId, httpMethod, endpoint, queries.size(), getDuration().toMillis());
    }
}
