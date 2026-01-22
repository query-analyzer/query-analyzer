package io.queryanalyzer.core.storage;

import io.queryanalyzer.core.model.Severity;
import io.queryanalyzer.core.storage.model.EndpointStatistics;
import io.queryanalyzer.core.storage.model.StoredIssue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


public class InMemoryIssueStorage implements IssueStorage {
    
    private static final Logger log = LoggerFactory.getLogger(InMemoryIssueStorage.class);
    
    private final ConcurrentHashMap<String, List<StoredIssue>> issuesByEndpoint;
    private final ConcurrentLinkedQueue<StoredIssue> allIssues;
    private final int maxSize;
    private final ScheduledExecutorService cleanupScheduler;
    
    // Lock for operations that modify both data structures
    private final Object storageLock = new Object();
    

    public InMemoryIssueStorage() {
        this(1000);
    }
    

    public InMemoryIssueStorage(int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("Max size must be positive: " + maxSize);
        }
        
        this.maxSize = maxSize;
        this.issuesByEndpoint = new ConcurrentHashMap<>();
        this.allIssues = new ConcurrentLinkedQueue<>();
        
        this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "query-analyzer-cleanup");
            t.setDaemon(true); // Don't prevent JVM shutdown
            return t;
        });
        
        // Clean up issues older than 7 days, every hour
        cleanupScheduler.scheduleAtFixedRate(
            this::performScheduledCleanup,
            1, // Initial delay: 1 hour
            1, // Period: 1 hour
            TimeUnit.HOURS
        );
        
        log.info("InMemoryIssueStorage initialized with max size: {}, auto-cleanup: every hour", maxSize);
    }
    

    private void performScheduledCleanup() {
        try {
            int removed = cleanup(Duration.ofDays(7));
            if (removed > 0) {
                log.debug("Auto-cleanup removed {} old issues", removed);
            }
        } catch (Exception e) {
            log.error("Scheduled cleanup failed", e);
        }
    }
    

    public void shutdown() {
        if (cleanupScheduler != null && !cleanupScheduler.isShutdown()) {
            cleanupScheduler.shutdown();
            try {
                if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    cleanupScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                cleanupScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.debug("InMemoryIssueStorage cleanup scheduler shut down");
        }
    }
    
    @Override
    public void store(StoredIssue issue) {
        if (issue == null) {
            throw new IllegalArgumentException("Issue cannot be null");
        }
        
        synchronized (storageLock) {
            allIssues.add(issue);
            
            issuesByEndpoint
                .computeIfAbsent(issue.getEndpoint(), k -> new CopyOnWriteArrayList<>())
                .add(issue);
            
            if (allIssues.size() > maxSize) {
                int removed = cleanupInternal(Duration.ofHours(24));
                if (removed == 0 && allIssues.size() > maxSize) {
                    removeOldestInternal(allIssues.size() - maxSize);
                }
            }
        }
        
        log.trace("Stored issue: {}", issue.getSummary());
    }
    
    @Override
    public List<StoredIssue> getByEndpoint(String endpoint, Duration timeWindow) {
        if (endpoint == null || timeWindow == null) {
            return Collections.emptyList();
        }
        
        Instant cutoff = Instant.now().minus(timeWindow);
        
        return issuesByEndpoint.getOrDefault(endpoint, Collections.emptyList()).stream()
            .filter(issue -> issue.getTimestamp().isAfter(cutoff))
            .sorted(Comparator.comparing(StoredIssue::getTimestamp).reversed())
            .collect(Collectors.toList());
    }
    
    @Override
    public List<StoredIssue> getRecent(Duration timeWindow) {
        if (timeWindow == null) {
            return Collections.emptyList();
        }
        
        Instant cutoff = Instant.now().minus(timeWindow);
        
        return allIssues.stream()
            .filter(issue -> issue.getTimestamp().isAfter(cutoff))
            .sorted(Comparator.comparing(StoredIssue::getTimestamp).reversed())
            .collect(Collectors.toList());
    }
    
    @Override
    public EndpointStatistics getStatistics(String endpoint, Duration timeWindow) {
        List<StoredIssue> issues = getByEndpoint(endpoint, timeWindow);
        
        if (issues.isEmpty()) {
            return null;
        }
        
        return buildStatistics(endpoint, issues);
    }
    
    @Override
    public List<EndpointStatistics> getAllStatistics(Duration timeWindow) {
        Map<String, List<StoredIssue>> issuesByEndpoint = getRecent(timeWindow).stream()
            .collect(Collectors.groupingBy(StoredIssue::getEndpoint));
        
        return issuesByEndpoint.entrySet().stream()
            .map(entry -> buildStatistics(entry.getKey(), entry.getValue()))
            .sorted(Comparator.comparingLong(EndpointStatistics::getPriorityScore).reversed())
            .collect(Collectors.toList());
    }
    
    @Override
    public int cleanup(Duration olderThan) {
        if (olderThan == null) {
            return 0;
        }
        synchronized (storageLock) {
            return cleanupInternal(olderThan);
        }
    }
    
    // Internal cleanup method - must be called with storageLock held
    private int cleanupInternal(Duration olderThan) {
        Instant cutoff = Instant.now().minus(olderThan);
        int removedCount = 0;
        
        // Remove from all issues
        Iterator<StoredIssue> iter = allIssues.iterator();
        while (iter.hasNext()) {
            if (iter.next().getTimestamp().isBefore(cutoff)) {
                iter.remove();
                removedCount++;
            }
        }
        
        // Remove from endpoint maps
        for (List<StoredIssue> issues : issuesByEndpoint.values()) {
            issues.removeIf(issue -> issue.getTimestamp().isBefore(cutoff));
        }
        
        // Remove empty endpoint entries
        issuesByEndpoint.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        
        if (removedCount > 0) {
            log.debug("Cleaned up {} old issues", removedCount);
        }
        
        return removedCount;
    }
    
    @Override
    public int size() {
        return allIssues.size();
    }
    
    @Override
    public void clear() {
        synchronized (storageLock) {
            allIssues.clear();
            issuesByEndpoint.clear();
        }
        // Note: We intentionally do NOT shutdown the cleanup scheduler here.
        // clear() is for clearing data, not for destroying the storage instance.
        // Use shutdown() explicitly when you want to stop the scheduler.
        log.info("Cleared all stored issues");
    }
    

    public void clearAndShutdown() {
        clear();
        shutdown();
        log.info("Cleared all issues and shut down cleanup scheduler");
    }
    

    // Internal removeOldest - must be called with storageLock held
    private void removeOldestInternal(int count) {
        for (int i = 0; i < count && !allIssues.isEmpty(); i++) {
            StoredIssue removed = allIssues.poll();
            if (removed != null) {
                List<StoredIssue> endpointIssues = issuesByEndpoint.get(removed.getEndpoint());
                if (endpointIssues != null) {
                    endpointIssues.remove(removed);
                }
            }
        }
        log.debug("Removed {} oldest issues to stay under size limit", count);
    }
    

    private EndpointStatistics buildStatistics(String endpoint, List<StoredIssue> issues) {
        Map<Severity, Long> bySeverity = issues.stream()
            .collect(Collectors.groupingBy(
                StoredIssue::getSeverity, 
                Collectors.counting()
            ));
        
        double avgQueries = issues.stream()
            .mapToInt(StoredIssue::getQueryCount)
            .average()
            .orElse(0.0);
        
        double avgTime = issues.stream()
            .mapToLong(StoredIssue::getExecutionTimeMs)
            .average()
            .orElse(0.0);
        
        Instant first = issues.stream()
            .map(StoredIssue::getTimestamp)
            .min(Instant::compareTo)
            .orElse(null);
        
        Instant last = issues.stream()
            .map(StoredIssue::getTimestamp)
            .max(Instant::compareTo)
            .orElse(null);
        
        return EndpointStatistics.builder()
            .endpoint(endpoint)
            .totalIssues(issues.size())
            .criticalCount(bySeverity.getOrDefault(Severity.CRITICAL, 0L))
            .errorCount(bySeverity.getOrDefault(Severity.ERROR, 0L))
            .warningCount(bySeverity.getOrDefault(Severity.WARNING, 0L))
            .infoCount(bySeverity.getOrDefault(Severity.INFO, 0L))
            .avgQueriesPerRequest(avgQueries)
            .avgExecutionTimeMs(avgTime)
            .firstSeen(first)
            .lastSeen(last)
            .build();
    }
}
