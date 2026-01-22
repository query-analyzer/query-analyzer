package io.queryanalyzer.core.detector;

import io.queryanalyzer.core.model.QueryInfo;
import io.queryanalyzer.core.model.QueryIssue;

import java.util.List;


public interface QueryDetector {
    

    String getName();
    
    List<QueryIssue> detect(List<QueryInfo> queries);
}
