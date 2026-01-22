package io.queryanalyzer.spring.service;

import io.queryanalyzer.core.detector.QueryDetector;
import io.queryanalyzer.core.metrics.MetricsCollector;
import io.queryanalyzer.core.model.QueryInfo;
import io.queryanalyzer.core.model.QueryIssue;
import io.queryanalyzer.core.model.IssueType;
import io.queryanalyzer.core.model.Severity;
import io.queryanalyzer.core.plan.QueryPlanAnalyzerFactory;
import io.queryanalyzer.core.reporter.QueryReporter;
import io.queryanalyzer.core.storage.IssueStorage;
import io.queryanalyzer.core.tracker.QueryTracker;
import io.queryanalyzer.spring.config.QueryAnalyzerProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

class QueryAnalysisOrchestratorTest {

    private QueryDetector detector1;
    private QueryDetector detector2;
    private QueryReporter reporter;
    private QueryAnalyzerProperties properties;
    private MetricsCollector metricsCollector;
    private IssueStorage storage;
    private DataSource dataSource;
    private QueryPlanAnalyzerFactory planAnalyzerFactory;
    private QueryAnalysisOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        detector1 = mock(QueryDetector.class);
        detector2 = mock(QueryDetector.class);
        reporter = mock(QueryReporter.class);
        metricsCollector = mock(MetricsCollector.class);
        storage = mock(IssueStorage.class);
        dataSource = mock(DataSource.class);
        planAnalyzerFactory = mock(QueryPlanAnalyzerFactory.class);
        properties = new QueryAnalyzerProperties();

        when(detector1.getName()).thenReturn("detector1");
        when(detector2.getName()).thenReturn("detector2");

        orchestrator = new QueryAnalysisOrchestrator(
            List.of(detector1, detector2),
            List.of(reporter),
            properties,
            metricsCollector,
            storage,
            dataSource,
            planAnalyzerFactory
        );

        QueryTracker.startTracking();
    }

    @AfterEach
    void tearDown() {
        QueryTracker.clear();
    }

    @Test
    void shouldNotAnalyzeWhenDisabled() {

        properties.setEnabled(false);
        QueryTracker.recordQuery("SELECT * FROM users", 10);


        orchestrator.analyzeAndReport();


        verifyNoInteractions(detector1, detector2, reporter);
    }

    @Test
    void shouldNotAnalyzeWhenNoQueries() {

        orchestrator.analyzeAndReport();


        verifyNoInteractions(reporter);
    }

    @Test
    void shouldRunEnabledDetectors() {

        QueryTracker.recordQuery("SELECT * FROM users", 10);


        orchestrator.analyzeAndReport();


        verify(detector1).detect(anyList());
        verify(detector2).detect(anyList());
    }

    @Test
    void shouldReportToAllReporters() {
        QueryReporter reporter2 = mock(QueryReporter.class);
        QueryAnalysisOrchestrator multiReporterOrchestrator = new QueryAnalysisOrchestrator(
            List.of(detector1),
            List.of(reporter, reporter2),
            properties,
            metricsCollector,
            storage,
            dataSource,
            planAnalyzerFactory
        );
        
        QueryIssue mockIssue = mock(QueryIssue.class);
        when(mockIssue.getType()).thenReturn(IssueType.N_PLUS_ONE);
        when(mockIssue.getSeverity()).thenReturn(Severity.WARNING);
        when(detector1.detect(anyList())).thenReturn(List.of(mockIssue));
        
        QueryTracker.recordQuery("SELECT * FROM users", 10);
        
        multiReporterOrchestrator.analyzeAndReport();
        
        verify(reporter).report(anyList());
        verify(reporter2).report(anyList());
    }
    
    @Test
    void shouldRecordMetricsWhenIssuesFound() {
        QueryIssue mockIssue = mock(QueryIssue.class);
        when(mockIssue.getType()).thenReturn(IssueType.N_PLUS_ONE);
        when(mockIssue.getSeverity()).thenReturn(Severity.WARNING);
        when(detector1.detect(anyList())).thenReturn(List.of(mockIssue));
        
        QueryTracker.recordQuery("SELECT * FROM users WHERE id = 1", 10);
        
        orchestrator.analyzeAndReport();
        
        verify(metricsCollector).recordRequestAnalyzed(eq(1), anyLong());
        verify(metricsCollector).recordIssue(IssueType.N_PLUS_ONE, Severity.WARNING);
    }
    
    @Test
    void shouldRecordMetricsEvenWhenNoIssues() {
        when(detector1.detect(anyList())).thenReturn(List.of());
        when(detector2.detect(anyList())).thenReturn(List.of());
        
        QueryTracker.recordQuery("SELECT * FROM users", 10);
        QueryTracker.recordQuery("SELECT * FROM orders", 15);
        
        orchestrator.analyzeAndReport();

        verify(metricsCollector).recordRequestAnalyzed(eq(2), anyLong());
        verify(metricsCollector, never()).recordIssue(any(), any());
    }
    
    @Test
    void shouldHandleNullMetricsCollectorGracefully() {
        QueryAnalysisOrchestrator orchestratorWithoutMetrics = new QueryAnalysisOrchestrator(
            List.of(detector1, detector2),
            List.of(reporter),
            properties,
            null,
            storage,
            dataSource,
            planAnalyzerFactory
        );
        
        QueryTracker.recordQuery("SELECT * FROM users", 10);
        
        orchestratorWithoutMetrics.analyzeAndReport();
        
        verify(detector1).detect(anyList());
    }
    
    @Test
    void shouldRecordMultipleIssuesInMetrics() {
        QueryIssue nPlusOneIssue = mock(QueryIssue.class);
        when(nPlusOneIssue.getType()).thenReturn(IssueType.N_PLUS_ONE);
        when(nPlusOneIssue.getSeverity()).thenReturn(Severity.ERROR);
        
        QueryIssue slowQueryIssue = mock(QueryIssue.class);
        when(slowQueryIssue.getType()).thenReturn(IssueType.SLOW_QUERY);
        when(slowQueryIssue.getSeverity()).thenReturn(Severity.WARNING);
        
        when(detector1.detect(anyList())).thenReturn(List.of(nPlusOneIssue));
        when(detector2.detect(anyList())).thenReturn(List.of(slowQueryIssue));
        
        QueryTracker.recordQuery("SELECT * FROM users WHERE id = 1", 10);
        QueryTracker.recordQuery("SELECT * FROM orders WHERE user_id = 1", 500);
        
        orchestrator.analyzeAndReport();
        
        verify(metricsCollector).recordIssue(IssueType.N_PLUS_ONE, Severity.ERROR);
        verify(metricsCollector).recordIssue(IssueType.SLOW_QUERY, Severity.WARNING);
        verify(metricsCollector).recordRequestAnalyzed(eq(2), anyLong());
    }
    
    @Test
    void shouldHandleDetectorException() {
        when(detector1.detect(anyList())).thenThrow(new RuntimeException("Detector failed"));
        when(detector2.detect(anyList())).thenReturn(List.of());
        
        QueryTracker.recordQuery("SELECT * FROM users", 10);
        
        // Should not throw - gracefully handles detector errors
        orchestrator.analyzeAndReport();
        
        verify(detector1).detect(anyList());
        verify(detector2).detect(anyList());
    }
    
    @Test
    void shouldHandleReporterException() {
        QueryIssue mockIssue = mock(QueryIssue.class);
        when(mockIssue.getType()).thenReturn(IssueType.N_PLUS_ONE);
        when(detector1.detect(anyList())).thenReturn(List.of(mockIssue));
        
        doThrow(new RuntimeException("Reporter failed")).when(reporter).report(anyList());
        
        QueryTracker.recordQuery("SELECT * FROM users", 10);
        
        orchestrator.analyzeAndReport();
        
        verify(reporter).report(anyList());
    }
}
