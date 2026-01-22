package io.queryanalyzer.test;

import io.queryanalyzer.core.model.QueryIssue;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class NoNPlusOneExtension implements BeforeEachCallback, AfterEachCallback {
    
    @Override
    public void beforeEach(ExtensionContext context) {
        if (hasAnnotation(context)) {
            QueryRecorder.start();
        }
    }
    
    @Override
    public void afterEach(ExtensionContext context) {
        NoNPlusOne annotation = getAnnotation(context);
        if (annotation == null) {
            return;
        }
        
        Set<String> ignoreTables = new HashSet<>();
        if (annotation.ignore() != null) {
            for (String table : annotation.ignore()) {
                ignoreTables.add(table);
            }
        }
        
        List<QueryIssue> issues = QueryRecorder.stopAndAnalyze(
            annotation.threshold(), 
            ignoreTables
        );
        
        if (!issues.isEmpty()) {
            throw new NPlusOneDetectedException(issues);
        }
    }
    
    private boolean hasAnnotation(ExtensionContext context) {
        return context.getRequiredTestMethod().isAnnotationPresent(NoNPlusOne.class);
    }
    
    private NoNPlusOne getAnnotation(ExtensionContext context) {
        return context.getRequiredTestMethod().getAnnotation(NoNPlusOne.class);
    }
}
