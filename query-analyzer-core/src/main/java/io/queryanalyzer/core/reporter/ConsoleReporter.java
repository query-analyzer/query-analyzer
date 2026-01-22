package io.queryanalyzer.core.reporter;

import io.queryanalyzer.core.model.*;
import io.queryanalyzer.core.plan.model.QueryPlanResult;

import java.io.PrintWriter;
import java.util.List;


public class ConsoleReporter implements QueryReporter {

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_BLUE = "\u001B[34m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_BOLD = "\u001B[1m";
    private static final String ANSI_DIM = "\u001B[2m";

    private static final String SEPARATOR = "--------------------------------------------------------------------------------";
    private static final int LABEL_WIDTH = 16;

    private final ReporterConfig config;
    private final PrintWriter output;

    public ConsoleReporter() {
        this(new ReporterConfig());
    }

    public ConsoleReporter(ReporterConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Config cannot be null");
        }
        this.config = config;
        this.output = config.getOutput() != null
            ? config.getOutput() 
            : new PrintWriter(System.out, true);
    }

    @Override
    public void report(List<QueryIssue> issues) {
        if (issues == null || issues.isEmpty()) {
            return;
        }
        
        List<QueryIssue> filtered = issues.stream()
            .filter(issue -> shouldReport(issue.getSeverity()))
            .toList();
        
        if (filtered.isEmpty()) {
            return;
        }
        
        for (QueryIssue issue : filtered) {
            printIssue(issue);
        }
        
        output.flush();
    }

    private boolean shouldReport(Severity severity) {
        return getSeverityLevel(severity) >= getSeverityLevel(config.getMinimumSeverity());
    }

    private int getSeverityLevel(Severity severity) {
        return switch (severity) {
            case INFO -> 0;
            case WARNING -> 1;
            case ERROR -> 2;
            case CRITICAL -> 3;
        };
    }

    public void report(AnalysisResult result) {
        if (result == null) {
            return;
        }

        if (!result.hasIssues()) {
            printSuccess(result);
            return;
        }

        List<QueryIssue> filtered = result.getIssues().stream()
            .filter(issue -> shouldReport(issue.getSeverity()))
            .toList();

        for (QueryIssue issue : filtered) {
            printIssue(issue);
        }

        printSummary(result, filtered);
        output.flush();
    }

    private void printSuccess(AnalysisResult result) {
        QueryStatistics stats = result.getStatistics();
        
        output.println();
        printSeparator();
        output.println();
        printHeader("OK", "No Issues Detected", Severity.INFO);
        output.println();
        printLabelValue("Requests", "1");
        printLabelValue("Total Queries", String.valueOf(stats.getTotalQueries()));
        printLabelValue("Total Time", stats.getTotalExecutionTimeMs() + "ms");
        printLabelValue("Avg per Query", stats.getAverageExecutionTimeMs() + "ms");
        output.println();
        printSeparator();
        output.println();
    }

    private void printIssue(QueryIssue issue) {
        output.println();
        printSeparator();
        output.println();
        
        printHeader(issue.getSeverity().name(), issue.getType().getDisplayName(), issue.getSeverity());
        output.println();
        
        if (issue.getEndpoint() != null && !issue.getEndpoint().isEmpty()) {
            String endpoint = issue.getEndpoint();
            if (issue.getHttpMethod() != null) {
                endpoint = issue.getHttpMethod() + " " + endpoint;
            }
            printLabelValue("Endpoint", endpoint);
        }
        
        if (issue.getLocation() != null && !issue.getLocation().isEmpty()) {
            printLabelValue("Location", issue.getLocation());
        }
        
        output.println();
        
        printLabelValue("Problem", issue.getDescription());
        
        if (config.isPrintMetrics() && issue.getMetrics() != null) {
            printMetrics(issue.getMetrics());
        }
        
        if (issue.getSampleQuery() != null && !issue.getSampleQuery().isEmpty()) {
            output.println();
            printLabel("Sample Query");
            output.println();
            printIndentedBlock(issue.getSampleQuery());
        }
        
        if (issue.getPlanResult() != null) {
            printQueryPlan(issue.getPlanResult());
        }
        
        if (config.isPrintSuggestions() && issue.getSuggestions() != null && !issue.getSuggestions().isEmpty()) {
            output.println();
            printSuggestions(issue.getSuggestions());
        }
        
        output.println();
        printSeparator();
    }

    private void printHeader(String level, String title, Severity severity) {
        String color = getColorForSeverity(severity);
        String reset = config.shouldUseColors() ? ANSI_RESET : "";
        String bold = config.shouldUseColors() ? ANSI_BOLD : "";
        
        output.println("  " + color + bold + level + reset + " | " + bold + title + reset);
    }

    private void printLabel(String label) {
        String dim = config.shouldUseColors() ? ANSI_DIM : "";
        String reset = config.shouldUseColors() ? ANSI_RESET : "";
        
        output.println("  " + dim + label + reset);
    }

    private void printLabelValue(String label, String value) {
        String dim = config.shouldUseColors() ? ANSI_DIM : "";
        String reset = config.shouldUseColors() ? ANSI_RESET : "";
        
        String paddedLabel = String.format("%-" + LABEL_WIDTH + "s", label);
        output.println("  " + dim + paddedLabel + reset + value);
    }

    private void printLabelContinuation(String value) {
        String padding = String.format("%-" + LABEL_WIDTH + "s", "");
        output.println("  " + padding + value);
    }

    private void printIndentedBlock(String text) {
        String[] lines = text.split("\n");
        for (String line : lines) {
            output.println("      " + line.trim());
        }
    }

    private void printMetrics(QueryMetrics metrics) {
        StringBuilder sb = new StringBuilder();
        sb.append("Total: ").append(metrics.getExecutionTimeMs()).append("ms");
        
        if (metrics.getQueryCount() > 1) {
            long avg = metrics.getExecutionTimeMs() / metrics.getQueryCount();
            sb.append(" | Avg: ").append(avg).append("ms per query");
        }
        
        printLabelContinuation(sb.toString());
        
        if (metrics.getPotentialImprovementPercent() > 0) {
            printLabelContinuation("Potential improvement: " + 
                String.format("%.0f%%", metrics.getPotentialImprovementPercent()));
        }
    }

    private void printQueryPlan(QueryPlanResult plan) {
        if (plan == null) {
            return;
        }
        
        output.println();
        printLabel("Query Plan");
        output.println();
        
        if (plan.getSummary() != null && !plan.getSummary().isEmpty()) {
            printIndentedBlock(plan.getSummary());
        }
        
        if (plan.isFullTableScan()) {
            output.println();
            printIndentedBlock("! Full table scan detected");
        }
        
        if (!plan.isUsesIndex() && plan.getEstimatedRows() > 1000) {
            printIndentedBlock("! No index used, scanning " + plan.getEstimatedRows() + " rows");
        }
        
        if (plan.getRecommendations() != null && !plan.getRecommendations().isEmpty()) {
            output.println();
            for (String rec : plan.getRecommendations()) {
                printIndentedBlock("- " + rec);
            }
        }
    }

    private void printSuggestions(List<String> suggestions) {
        if (suggestions == null || suggestions.isEmpty()) {
            return;
        }
        
        // Filter out empty suggestions
        List<String> filtered = suggestions.stream()
            .filter(s -> s != null && !s.trim().isEmpty())
            .toList();
        
        if (filtered.isEmpty()) {
            return;
        }
        
        boolean firstItem = true;
        
        for (String suggestion : filtered) {
            // Determine if this is a header/context line or an actionable fix
            boolean isHeader = suggestion.startsWith("Confidence:") || 
                               suggestion.startsWith("Relationship:") ||
                               suggestion.contains("detected.") ||
                               suggestion.contains("Common fixes:");
            
            // Actionable fixes that should get bullet points
            boolean isActionableFix = !isHeader && 
                (suggestion.startsWith("Use ") ||
                 suggestion.startsWith("Add ") ||
                 suggestion.startsWith("Replace ") ||
                 suggestion.startsWith("Collect ") ||
                 suggestion.startsWith("Rewrite ") ||
                 suggestion.startsWith("Batch ") ||
                 suggestion.startsWith("JOIN "));
            
            if (firstItem) {
                printLabelValue("Suggestions", suggestion);
                firstItem = false;
            } else if (isHeader) {
                output.println();
                printLabelContinuation(suggestion);
            } else if (isActionableFix) {
                printLabelContinuation("- " + suggestion);
            } else {
                printLabelContinuation(suggestion);
            }
        }
    }

    private void printSummary(AnalysisResult result, List<QueryIssue> issues) {
        QueryStatistics stats = result.getStatistics();
        
        output.println();
        printSeparator();
        output.println();
        
        String bold = config.shouldUseColors() ? ANSI_BOLD : "";
        String reset = config.shouldUseColors() ? ANSI_RESET : "";
        output.println("  " + bold + "Summary" + reset);
        output.println();
        
        printLabelValue("Requests", "1");
        printLabelValue("Total Queries", String.valueOf(stats.getTotalQueries()));
        printLabelValue("Total Time", stats.getTotalExecutionTimeMs() + "ms");
        output.println();
        
        printLabelValue("Issues", formatIssueCounts(issues));
        
        output.println();
        printSeparator();
        output.println();
    }

    private String formatIssueCounts(List<QueryIssue> issues) {
        long critical = issues.stream().filter(i -> i.getSeverity() == Severity.CRITICAL).count();
        long error = issues.stream().filter(i -> i.getSeverity() == Severity.ERROR).count();
        long warning = issues.stream().filter(i -> i.getSeverity() == Severity.WARNING).count();
        long info = issues.stream().filter(i -> i.getSeverity() == Severity.INFO).count();
        
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        
        if (critical > 0) {
            sb.append(critical).append(" critical");
            first = false;
        }
        if (error > 0) {
            if (!first) sb.append(", ");
            sb.append(error).append(" error");
            if (error > 1) sb.append("s");
            first = false;
        }
        if (warning > 0) {
            if (!first) sb.append(", ");
            sb.append(warning).append(" warning");
            if (warning > 1) sb.append("s");
            first = false;
        }
        if (info > 0) {
            if (!first) sb.append(", ");
            sb.append(info).append(" info");
        }
        
        return sb.toString();
    }

    private void printSeparator() {
        String dim = config.shouldUseColors() ? ANSI_DIM : "";
        String reset = config.shouldUseColors() ? ANSI_RESET : "";
        output.println(dim + SEPARATOR + reset);
    }

    private String getColorForSeverity(Severity severity) {
        if (!config.shouldUseColors()) {
            return "";
        }

        return switch (severity) {
            case CRITICAL -> ANSI_RED + ANSI_BOLD;
            case ERROR -> ANSI_RED;
            case WARNING -> ANSI_YELLOW;
            case INFO -> ANSI_BLUE;
        };
    }
}
