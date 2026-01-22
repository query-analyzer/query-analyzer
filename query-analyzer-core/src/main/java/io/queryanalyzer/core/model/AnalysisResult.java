package io.queryanalyzer.core.model;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;

import java.time.Duration;
import java.time.Instant;
import java.util.List;


@Getter
@Builder
public final class AnalysisResult {

    @Singular
    private final List<QueryIssue> issues;
    
    @NonNull
    private final QueryStatistics statistics;
    
    @NonNull
    private final Instant analyzedAt;
    
    @NonNull
    private final Duration analysisTime;


    public boolean hasIssues() {
        return !issues.isEmpty();
    }



    public List<QueryIssue> getCriticalIssues() {
        return issues.stream()
            .filter(issue -> issue.getSeverity() == Severity.CRITICAL)
            .toList();
    }


    public List<QueryIssue> getIssuesByType(IssueType type) {
        if (type == null) {
            return List.of();
        }
        return issues.stream()
            .filter(issue -> issue.getType() == type)
            .toList();
    }


    public List<QueryIssue> getIssuesBySeverity(Severity severity) {
        if (severity == null) {
            return List.of();
        }
        return issues.stream()
            .filter(issue -> issue.getSeverity() == severity)
            .toList();
    }

    @Override
    public String toString() {
        return "AnalysisResult{" +
            "issueCount=" + issues.size() +
            ", statistics=" + statistics +
            ", analyzedAt=" + analyzedAt +
            ", analysisTime=" + analysisTime +
            '}';
    }
}
