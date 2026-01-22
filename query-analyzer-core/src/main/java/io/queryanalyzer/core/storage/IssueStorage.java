package io.queryanalyzer.core.storage;

import io.queryanalyzer.core.storage.model.EndpointStatistics;
import io.queryanalyzer.core.storage.model.StoredIssue;

import java.time.Duration;
import java.util.List;


public interface IssueStorage {
    

    void store(StoredIssue issue);
    

    List<StoredIssue> getByEndpoint(String endpoint, Duration timeWindow);
    

    List<StoredIssue> getRecent(Duration timeWindow);
    

    EndpointStatistics getStatistics(String endpoint, Duration timeWindow);
    

    List<EndpointStatistics> getAllStatistics(Duration timeWindow);
    

    int cleanup(Duration olderThan);
    

    int size();

    void clear();
}
