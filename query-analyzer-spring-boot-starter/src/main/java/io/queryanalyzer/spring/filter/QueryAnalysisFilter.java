package io.queryanalyzer.spring.filter;

import io.queryanalyzer.core.context.RequestContextHolder;
import io.queryanalyzer.spring.service.QueryAnalysisOrchestrator;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;


public class QueryAnalysisFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(QueryAnalysisFilter.class);

    private final QueryAnalysisOrchestrator orchestrator;

    public QueryAnalysisFilter(QueryAnalysisOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException {

        String endpoint = null;
        String method = null;
        String userId = null;
        
        if (request instanceof HttpServletRequest httpRequest) {
            endpoint = httpRequest.getRequestURI();
            method = httpRequest.getMethod();
            userId = extractUserId(httpRequest);
        }

        // Start unified context tracking
        RequestContextHolder.start(endpoint, method, userId);

        try {
            chain.doFilter(request, response);

        } finally {
            try {
                orchestrator.analyzeAndReport();
            } catch (Exception e) {
                log.error("Query analysis failed for request: {} {}", method, endpoint, e);
            } finally {
                RequestContextHolder.clear();
            }
        }
    }
    

    protected String extractUserId(HttpServletRequest request) {
        String userId = request.getHeader("X-User-Id");
        if (userId != null && !userId.isEmpty()) {
            return userId;
        }
        
        userId = request.getHeader("X-Request-User");
        if (userId != null && !userId.isEmpty()) {
            return userId;
        }
        
        return null;
    }

    @Override
    public void init(FilterConfig filterConfig) {
        log.info("Query Analysis Filter initialized");
    }

    @Override
    public void destroy() {
        log.info("Query Analysis Filter destroyed");
    }
}
