package io.queryanalyzer.core.reporter;

import io.queryanalyzer.core.model.QueryIssue;

import java.util.List;


public interface QueryReporter {
    void report(List<QueryIssue> issues);
}
