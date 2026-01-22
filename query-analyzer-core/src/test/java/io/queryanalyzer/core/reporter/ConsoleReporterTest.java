package io.queryanalyzer.core.reporter;

import io.queryanalyzer.core.model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConsoleReporterTest {

    private ConsoleReporter reporter;
    private ByteArrayOutputStream outputStream;
    private PrintStream originalOut;

    @BeforeEach
    void setUp() {
        outputStream = new ByteArrayOutputStream();
        originalOut = System.out;
        System.setOut(new PrintStream(outputStream));

        ReporterConfig config = new ReporterConfig();
        config.setColorEnabled(false);
        reporter = new ConsoleReporter(config);
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
    }

    @Test
    void shouldReportSuccessWhenNoIssues() {
        AnalysisResult result = createResultWithNoIssues();

        reporter.report(result);

        String output = outputStream.toString();
        assertThat(output).contains("OK");
        assertThat(output).contains("No Issues Detected");
        assertThat(output).contains("Total Queries");
    }

    @Test
    void shouldReportIssuesWithDetails() {
        AnalysisResult result = createResultWithIssues();

        reporter.report(result);

        String output = outputStream.toString();
        assertThat(output).contains("ERROR");
        assertThat(output).contains("N+1 Query Detected");
        assertThat(output).contains("Location");
        assertThat(output).contains("Problem");
    }

    @Test
    void shouldPrintMetrics() {
        AnalysisResult result = createResultWithIssues();

        reporter.report(result);

        String output = outputStream.toString();
        assertThat(output).contains("Total:");
        assertThat(output).contains("ms");
    }

    @Test
    void shouldPrintSuggestions() {
        AnalysisResult result = createResultWithIssues();

        reporter.report(result);

        String output = outputStream.toString();
        assertThat(output).contains("Suggestions");
        assertThat(output).contains("@EntityGraph");
    }

    @Test
    void shouldPrintSummary() {
        AnalysisResult result = createResultWithMultipleSeverities();

        reporter.report(result);

        String output = outputStream.toString();
        assertThat(output).contains("Summary");
        assertThat(output).contains("error");
        assertThat(output).contains("warning");
    }

    @Test
    void shouldRespectMinimumSeverityFilter() {
        ReporterConfig config = new ReporterConfig();
        config.setColorEnabled(false);
        config.setMinimumSeverity(Severity.ERROR);
        ConsoleReporter filteredReporter = new ConsoleReporter(config);

        AnalysisResult result = createResultWithMultipleSeverities();

        filteredReporter.report(result);

        String output = outputStream.toString();
        assertThat(output).contains("ERROR");
    }

    @Test
    void shouldHandleNullResult() {
        reporter.report((AnalysisResult) null);
    }
    
    @Test
    void shouldDisplayEndpointWhenAvailable() {
        QueryIssue issue = QueryIssue.builder()
            .type(IssueType.N_PLUS_ONE)
            .severity(Severity.WARNING)
            .description("Test issue")
            .location("TestController.test:42")
            .endpoint("/api/users")
            .httpMethod("GET")
            .metrics(new QueryMetrics(100, 5, 80.0))
            .suggestions(List.of("Fix it"))
            .detectedAt(Instant.now())
            .build();
        
        reporter.report(List.of(issue));
        
        String output = outputStream.toString();
        assertThat(output).contains("GET /api/users");
    }
    
    @Test
    void shouldDisplaySampleQuery() {
        QueryIssue issue = QueryIssue.builder()
            .type(IssueType.SLOW_QUERY)
            .severity(Severity.WARNING)
            .description("Test issue")
            .location("TestController.test:42")
            .sampleQuery("SELECT * FROM users WHERE id = ?")
            .metrics(new QueryMetrics(100, 1, 50.0))
            .suggestions(List.of("Add index"))
            .detectedAt(Instant.now())
            .build();
        
        reporter.report(List.of(issue));
        
        String output = outputStream.toString();
        assertThat(output).contains("Sample Query");
        assertThat(output).contains("SELECT * FROM users");
    }

    private AnalysisResult createResultWithNoIssues() {
        QueryStatistics stats = new QueryStatistics(5, 100, 20, 50, 5, 0);
        return AnalysisResult.builder()
            .statistics(stats)
            .analyzedAt(Instant.now())
            .analysisTime(Duration.ofMillis(10))
            .build();
    }

    private AnalysisResult createResultWithIssues() {
        List<QueryIssue> issues = List.of(
            createIssue(IssueType.N_PLUS_ONE, Severity.ERROR)
        );

        QueryStatistics stats = new QueryStatistics(10, 500, 50, 200, 5, 5);

        return AnalysisResult.builder()
            .issues(issues)
            .statistics(stats)
            .analyzedAt(Instant.now())
            .analysisTime(Duration.ofMillis(15))
            .build();
    }

    private AnalysisResult createResultWithMultipleSeverities() {
        List<QueryIssue> issues = List.of(
            createIssue(IssueType.N_PLUS_ONE, Severity.ERROR),
            createIssue(IssueType.SLOW_QUERY, Severity.WARNING),
            createIssue(IssueType.SLOW_QUERY, Severity.INFO)
        );

        QueryStatistics stats = new QueryStatistics(10, 500, 50, 200, 5, 5);

        return AnalysisResult.builder()
            .issues(issues)
            .statistics(stats)
            .analyzedAt(Instant.now())
            .analysisTime(Duration.ofMillis(15))
            .build();
    }

    private QueryIssue createIssue(IssueType type, Severity severity) {
        QueryMetrics metrics = new QueryMetrics(250, 10, 90.0);

        List<String> suggestions = List.of(
            "Use @EntityGraph",
            "@EntityGraph(attributePaths = \"orders\")"
        );

        return QueryIssue.builder()
            .type(type)
            .severity(severity)
            .description("Test issue description")
            .location("TestController.test:42")
            .endpoint("/api/test")
            .httpMethod("GET")
            .metrics(metrics)
            .suggestions(suggestions)
            .detectedAt(Instant.now())
            .build();
    }
}
