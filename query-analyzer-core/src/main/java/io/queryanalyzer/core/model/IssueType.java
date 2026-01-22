package io.queryanalyzer.core.model;

// Types of query performance issues that can be detected.
public enum IssueType {


    N_PLUS_ONE("N+1 Query Detected"),

    SLOW_QUERY("Slow Query");

    private final String displayName;

    IssueType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
