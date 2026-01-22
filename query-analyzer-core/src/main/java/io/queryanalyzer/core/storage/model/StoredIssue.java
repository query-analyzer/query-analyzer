package io.queryanalyzer.core.storage.model;

import io.queryanalyzer.core.model.IssueType;
import io.queryanalyzer.core.model.Severity;
import io.queryanalyzer.core.plan.model.QueryPlanResult;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;


@Value
@Builder
public class StoredIssue {
    

    String id;
    

    Instant timestamp;
    

    String endpoint;
    

    String httpMethod;
    

    IssueType type;
    

    Severity severity;
    

    String description;
    

    int queryCount;
    

    long executionTimeMs;
    

    String sampleQuery;
    

    String location;
    

    String requestId;
    

    String userId;
    

    QueryPlanResult planResult;

    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[%s] %s %s: %s (%d queries, %dms)",
            severity,
            httpMethod,
            endpoint,
            type,
            queryCount,
            executionTimeMs));
        
        if (planResult != null && planResult.hasPerformanceIssues()) {
            sb.append(" - Plan: ").append(planResult.getSummary());
        }
        
        return sb.toString();
    }
}
