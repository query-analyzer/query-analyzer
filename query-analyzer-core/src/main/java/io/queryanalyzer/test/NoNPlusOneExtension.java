package io.queryanalyzer.test;

import io.queryanalyzer.core.context.RequestContextHolder;
import io.queryanalyzer.core.model.QueryInfo;
import io.queryanalyzer.core.model.QueryIssue;
import io.queryanalyzer.core.tracker.QueryTracker;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class NoNPlusOneExtension implements BeforeEachCallback, AfterEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) {
        if (hasAnnotation(context)) {
            // Capture through the same tracker the JDBC proxy feeds, so the
            // test-time guard sees exactly the queries production would.
            QueryTracker.setEnabled(true);
            RequestContextHolder.clear();
            RequestContextHolder.start(context.getRequiredTestMethod().getName(), "TEST");
        }
    }

    @Override
    public void afterEach(ExtensionContext context) {
        NoNPlusOne annotation = getAnnotation(context);
        if (annotation == null) {
            return;
        }

        try {
            List<QueryInfo> queries = QueryTracker.getQueries();
            Set<String> ignoreTables = new HashSet<>(Arrays.asList(annotation.ignore()));

            List<QueryIssue> issues = QueryRecorder.analyze(
                queries,
                annotation.threshold(),
                ignoreTables
            );

            if (!issues.isEmpty()) {
                throw new NPlusOneDetectedException(issues);
            }
        } finally {
            RequestContextHolder.clear();
        }
    }

    private boolean hasAnnotation(ExtensionContext context) {
        return context.getRequiredTestMethod().isAnnotationPresent(NoNPlusOne.class);
    }

    private NoNPlusOne getAnnotation(ExtensionContext context) {
        return context.getRequiredTestMethod().getAnnotation(NoNPlusOne.class);
    }
}
