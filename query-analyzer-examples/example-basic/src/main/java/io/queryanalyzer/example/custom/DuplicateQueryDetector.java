package io.queryanalyzer.example.custom;

import io.queryanalyzer.core.analyzer.StackTraceFilter;
import io.queryanalyzer.core.detector.QueryDetector;
import io.queryanalyzer.core.model.*;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Component
public class DuplicateQueryDetector implements QueryDetector {

    private static final int DUPLICATE_THRESHOLD = 2;

    @Override
    public String getName() {
        return "duplicate-query";
    }

    @Override
    public List<QueryIssue> detect(List<QueryInfo> queries) {
        if (queries == null || queries.isEmpty()) {
            return List.of();
        }

        Map<String, Long> queryCounts = queries.stream()
                .collect(Collectors.groupingBy(
                        QueryInfo::getSql,
                        Collectors.counting()
                ));

        List<QueryIssue> issues = new ArrayList<>();

        queryCounts.entrySet().stream()
                .filter(entry -> entry.getValue() >= DUPLICATE_THRESHOLD)
                .forEach(entry -> {
                    String sql = entry.getKey();
                    long count = entry.getValue();

                    // Find the location from the first occurrence
                    List<QueryInfo> duplicates = queries.stream()
                            .filter(q -> q.getSql().equals(sql))
                            .collect(Collectors.toList());

                    String location = findApplicationCode(duplicates);
                    String endpoint = io.queryanalyzer.core.tracker.QueryTracker.getEndpoint();

                    QueryIssue issue = QueryIssue.builder()
                            .type(IssueType.N_PLUS_ONE)
                            .severity(determineSeverity(count))
                            .description(String.format(
                                    "Exact duplicate query executed %d times", count))
                            .location(location)
                            .endpoint(endpoint)
                            .sampleQuery(sql)
                            .detectedAt(Instant.now())
                            .suggestions(List.of(
                                    "Consider caching the query result",
                                    "Check if the logic can be refactored to execute once",
                                    "Use a request-scoped cache for this data"
                            ))
                            .build();

                    issues.add(issue);
                });

        return issues;
    }

    private Severity determineSeverity(long count) {
        if (count >= 10) {
            return Severity.ERROR;
        } else if (count >= 5) {
            return Severity.WARNING;
        } else {
            return Severity.INFO;
        }
    }

    private String findApplicationCode(List<QueryInfo> queries) {
        if (queries == null || queries.isEmpty()) {
            return "unknown";
        }

        QueryInfo firstQuery = queries.get(0);
        String location = StackTraceFilter.findApplicationCode(firstQuery.getStackTrace());

        if ("unknown".equals(location)) {
            String endpoint = io.queryanalyzer.core.tracker.QueryTracker.getEndpoint();
            if (endpoint != null && !endpoint.isEmpty()) {
                location = "Endpoint: " + endpoint;
            }
        }

        return location;
    }
}