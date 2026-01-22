package io.queryanalyzer.example.custom;

import io.queryanalyzer.core.model.QueryIssue;
import io.queryanalyzer.core.model.QueryMetrics;
import io.queryanalyzer.core.reporter.QueryReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;


@Component
@ConditionalOnProperty(name = "app.file-reporter.enabled", havingValue = "true", matchIfMissing = false)
public class FileReporter implements QueryReporter {
    
    private static final Logger log = LoggerFactory.getLogger(FileReporter.class);
    private static final String FILENAME = "query-issues.log";
    private static final DateTimeFormatter FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    @Override
    public void report(List<QueryIssue> issues) {
        if (issues == null || issues.isEmpty()) {
            return;
        }
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(FILENAME, true))) {
            writer.println("\n" + "=".repeat(60));
            writer.println("QUERY ANALYSIS REPORT");
            writer.println("=".repeat(60));
            writer.println("Time: " + LocalDateTime.now().format(FORMATTER));
            writer.println("Issues Found: " + issues.size());
            writer.println();
            
            for (int i = 0; i < issues.size(); i++) {
                QueryIssue issue = issues.get(i);
                
                writer.println((i + 1) + ". [" + issue.getSeverity() + "] " + issue.getType());
                writer.println("   Description: " + issue.getDescription());
                
                if (issue.getLocation() != null) {
                    writer.println("   Location: " + issue.getLocation());
                }
                
                if (issue.getSampleQuery() != null && !issue.getSampleQuery().isEmpty()) {
                    writer.println("   Query: " + truncate(issue.getSampleQuery(), 100));
                }
                
                if (issue.getMetrics() != null) {
                    QueryMetrics metrics = issue.getMetrics();
                    writer.println("   Metrics:");
                    writer.println("     - Execution Time: " + metrics.getExecutionTimeMs() + "ms");
                    writer.println("     - Query Count: " + metrics.getQueryCount());
                    if (metrics.getPotentialImprovementPercent() > 0) {
                        writer.println("     - Potential Improvement: " + 
                            metrics.getPotentialImprovementPercent() + "%");
                    }
                }
                
                writer.println();
            }
            
            writer.println("=".repeat(60));
            writer.println();
            writer.flush();
            
            log.info("Wrote {} issues to {}", issues.size(), FILENAME);
            
        } catch (IOException e) {
            log.error("Failed to write query issues to file", e);
        }
    }
    
    private String truncate(String str, int maxLength) {
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength) + "...";
    }
}
