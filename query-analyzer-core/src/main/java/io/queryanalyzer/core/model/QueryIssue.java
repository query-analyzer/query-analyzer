package io.queryanalyzer.core.model;

import io.queryanalyzer.core.plan.model.QueryPlanResult;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


@Getter
@Builder(toBuilder = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class QueryIssue {

    @EqualsAndHashCode.Include
    @NonNull
    private final IssueType type;
    
    @EqualsAndHashCode.Include
    @NonNull
    private final Severity severity;
    
    @EqualsAndHashCode.Include
    @NonNull
    private final String description;
    
    @EqualsAndHashCode.Include
    @Builder.Default
    private final String location = "unknown";
    
    private final String endpoint;
    
    private final String httpMethod;
    

    private final String sampleQuery;
    

    @Builder.Default
    private final List<String> suggestions = Collections.emptyList();
    
    private final QueryMetrics metrics;
    
    @NonNull
    private final Instant detectedAt;
    

    private final QueryPlanResult planResult;
    
    /**
     * Returns an unmodifiable view of suggestions.
     * To add suggestions, use toBuilder().suggestions(newList).build()
     */
    public List<String> getSuggestions() {
        return suggestions == null ? Collections.emptyList() : Collections.unmodifiableList(suggestions);
    }
    
    /**
     * Creates a new QueryIssue with the plan result attached.
     * This preserves immutability.
     */
    public QueryIssue withPlanResult(QueryPlanResult planResult) {
        return this.toBuilder().planResult(planResult).build();
    }
    
    /**
     * Creates a new QueryIssue with additional suggestions.
     * This preserves immutability.
     */
    public QueryIssue withAdditionalSuggestions(List<String> additionalSuggestions) {
        if (additionalSuggestions == null || additionalSuggestions.isEmpty()) {
            return this;
        }
        List<String> combined = new ArrayList<>(this.suggestions);
        combined.addAll(additionalSuggestions);
        return this.toBuilder().suggestions(combined).build();
    }
    

    @Override
    public String toString() {
        return "QueryIssue{" +
            "type=" + type +
            ", severity=" + severity +
            ", description='" + description + '\'' +
            ", location='" + location + '\'' +
            ", hasPlan=" + (planResult != null) +
            '}';
    }
}
