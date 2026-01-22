package io.queryanalyzer.test;

import io.queryanalyzer.core.model.QueryIssue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class QueryRecorderTest {
    
    @BeforeEach
    @AfterEach
    void cleanup() {
        QueryRecorder.stop();
    }
    
    @Test
    void detectsNPlusOnePattern() {
        QueryRecorder.start();
        
        for (int i = 1; i <= 5; i++) {
            QueryRecorder.record("SELECT * FROM orders WHERE user_id = " + i);
        }
        
        List<QueryIssue> issues = QueryRecorder.stopAndAnalyze(3, Set.of());
        
        assertFalse(issues.isEmpty(), "Should detect N+1 pattern");
        assertTrue(issues.get(0).getDescription().contains("orders"));
    }
    
    @Test
    void doesNotFlagBelowThreshold() {
        QueryRecorder.start();
        
        QueryRecorder.record("SELECT * FROM users WHERE id = 1");
        QueryRecorder.record("SELECT * FROM users WHERE id = 2");
        
        List<QueryIssue> issues = QueryRecorder.stopAndAnalyze(3, Set.of());
        
        assertTrue(issues.isEmpty(), "Should not flag below threshold");
    }
    
    @Test
    void respectsCustomThreshold() {
        QueryRecorder.start();
        
        for (int i = 1; i <= 4; i++) {
            QueryRecorder.record("SELECT * FROM users WHERE id = " + i);
        }
        
        // Threshold 5: should not detect
        List<QueryIssue> issues = QueryRecorder.stopAndAnalyze(5, Set.of());
        assertTrue(issues.isEmpty());
    }
    
    @Test
    void respectsIgnoreList() {
        QueryRecorder.start();
        
        for (int i = 1; i <= 10; i++) {
            QueryRecorder.record("SELECT * FROM audit_log WHERE id = " + i);
        }
        
        List<QueryIssue> issues = QueryRecorder.stopAndAnalyze(3, Set.of("audit_log"));
        
        assertTrue(issues.isEmpty(), "Should ignore audit_log table");
    }
    
    @Test
    void isRecordingReflectsState() {
        assertFalse(QueryRecorder.isRecording());
        
        QueryRecorder.start();
        assertTrue(QueryRecorder.isRecording());
        
        QueryRecorder.stop();
        assertFalse(QueryRecorder.isRecording());
    }
    
    @Test
    void ignoresRecordingWhenNotStarted() {
        QueryRecorder.record("SELECT 1");
        QueryRecorder.record("SELECT 2", 100);
        
        assertFalse(QueryRecorder.isRecording());
    }
    
    @Test
    void tracksRecordedCount() {
        assertEquals(0, QueryRecorder.getRecordedCount());
        
        QueryRecorder.start();
        assertEquals(0, QueryRecorder.getRecordedCount());
        
        QueryRecorder.record("SELECT 1");
        QueryRecorder.record("SELECT 2");
        assertEquals(2, QueryRecorder.getRecordedCount());
        
        QueryRecorder.stop();
        assertEquals(0, QueryRecorder.getRecordedCount());
    }
    
    @Test
    void handlesNullAndEmptySql() {
        QueryRecorder.start();
        
        QueryRecorder.record(null);
        QueryRecorder.record("");
        QueryRecorder.record("   ");
        
        assertEquals(0, QueryRecorder.getRecordedCount());
    }
}
