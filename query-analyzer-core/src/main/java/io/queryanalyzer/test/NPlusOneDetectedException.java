package io.queryanalyzer.test;

import io.queryanalyzer.core.model.QueryIssue;

import java.util.List;


public class NPlusOneDetectedException extends AssertionError {
    
    private final List<QueryIssue> issues;
    
    public NPlusOneDetectedException(List<QueryIssue> issues) {
        super(formatMessage(issues));
        this.issues = List.copyOf(issues);
    }
    

    public List<QueryIssue> getIssues() {
        return issues;
    }
    
    private static String formatMessage(List<QueryIssue> issues) {
        StringBuilder sb = new StringBuilder();
        sb.append("N+1 query pattern detected:\n\n");
        
        for (QueryIssue issue : issues) {
            sb.append("  ").append(issue.getDescription()).append("\n");
            
            if (issue.getLocation() != null && !issue.getLocation().equals("unknown")) {
                sb.append("    Location: ").append(issue.getLocation()).append("\n");
            }
            
            if (issue.getSampleQuery() != null) {
                String sample = issue.getSampleQuery();
                if (sample.length() > 100) {
                    sample = sample.substring(0, 100) + "...";
                }
                sb.append("    SQL: ").append(sample).append("\n");
            }
            
            if (issue.getSuggestions() != null && !issue.getSuggestions().isEmpty()) {
                sb.append("    Suggestions:\n");
                for (String suggestion : issue.getSuggestions()) {
                    if (suggestion != null && !suggestion.trim().isEmpty()) {
                        sb.append("      - ").append(suggestion).append("\n");
                    }
                }
            }
            
            sb.append("\n");
        }
        
        return sb.toString();
    }
}
