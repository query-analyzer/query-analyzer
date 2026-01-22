package io.queryanalyzer.core.storage;

import io.queryanalyzer.core.storage.model.EndpointStatistics;
import io.queryanalyzer.core.storage.model.StoredIssue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class FileIssueStorage implements IssueStorage {
    
    private static final Logger log = LoggerFactory.getLogger(FileIssueStorage.class);
    
    private final Path storageDir;
    private final InMemoryIssueStorage cache;
    

    public FileIssueStorage() {
        this(Paths.get("./query-analyzer-data"));
    }
    

    public FileIssueStorage(Path storageDir) {
        if (storageDir == null) {
            throw new IllegalArgumentException("Storage directory cannot be null");
        }
        
        this.storageDir = storageDir;
        this.cache = new InMemoryIssueStorage(10000);
        
        createDirectoryIfNeeded();
        loadExistingIssues();
        
        log.info("FileIssueStorage initialized at: {}", storageDir.toAbsolutePath());
    }
    
    @Override
    public void store(StoredIssue issue) {
        if (issue == null) {
            throw new IllegalArgumentException("Issue cannot be null");
        }
        
        cache.store(issue);
        
        try {
            writeToFile(issue);
            log.trace("Stored issue to file: {}", issue.getId());
        } catch (IOException e) {
            log.error("Failed to write issue to file: {}", issue.getId(), e);
        }
    }
    
    @Override
    public List<StoredIssue> getByEndpoint(String endpoint, Duration timeWindow) {
        return cache.getByEndpoint(endpoint, timeWindow);
    }
    
    @Override
    public List<StoredIssue> getRecent(Duration timeWindow) {
        return cache.getRecent(timeWindow);
    }
    
    @Override
    public EndpointStatistics getStatistics(String endpoint, Duration timeWindow) {
        return cache.getStatistics(endpoint, timeWindow);
    }
    
    @Override
    public List<EndpointStatistics> getAllStatistics(Duration timeWindow) {
        return cache.getAllStatistics(timeWindow);
    }
    
    @Override
    public int cleanup(Duration olderThan) {
        int removed = cache.cleanup(olderThan);
        
        try {
            removed += cleanupOldFiles(olderThan);
        } catch (IOException e) {
            log.error("Failed to cleanup old files", e);
        }
        
        return removed;
    }
    
    @Override
    public int size() {
        return cache.size();
    }
    
    @Override
    public void clear() {
        cache.clear();
        try {
            deleteAllFiles();
        } catch (IOException e) {
            log.error("Failed to delete files", e);
        }
    }
    

    private void writeToFile(StoredIssue issue) throws IOException {
        LocalDate date = issue.getTimestamp()
            .atZone(ZoneId.systemDefault())
            .toLocalDate();
        
        Path dateDir = storageDir.resolve(date.toString());
        Files.createDirectories(dateDir);

        String filename = String.format("%s-%d-%s.txt",
            sanitizeEndpoint(issue.getEndpoint()),
            issue.getTimestamp().toEpochMilli(),
            issue.getId()
        );
        
        Path file = dateDir.resolve(filename);
        
        String content = formatIssue(issue);
        Files.writeString(file, content, StandardOpenOption.CREATE_NEW);
    }
    

    private String formatIssue(StoredIssue issue) {
        StringBuilder sb = new StringBuilder();
        sb.append("ID: ").append(issue.getId()).append("\n");
        sb.append("Timestamp: ").append(issue.getTimestamp()).append("\n");
        sb.append("Endpoint: ").append(issue.getEndpoint()).append("\n");
        sb.append("Method: ").append(issue.getHttpMethod()).append("\n");
        sb.append("Type: ").append(issue.getType()).append("\n");
        sb.append("Severity: ").append(issue.getSeverity()).append("\n");
        sb.append("Description: ").append(issue.getDescription()).append("\n");
        sb.append("QueryCount: ").append(issue.getQueryCount()).append("\n");
        sb.append("ExecutionTime: ").append(issue.getExecutionTimeMs()).append("\n");
        if (issue.getRequestId() != null) {
            sb.append("RequestID: ").append(issue.getRequestId()).append("\n");
        }
        if (issue.getUserId() != null) {
            sb.append("UserID: ").append(issue.getUserId()).append("\n");
        }
        if (issue.getLocation() != null) {
            sb.append("Location: ").append(issue.getLocation()).append("\n");
        }
        return sb.toString();
    }
    

    private String sanitizeEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isEmpty()) {
            return "unknown";
        }
        String sanitized = endpoint
            .replaceAll("[^a-zA-Z0-9-]", "-")
            .replaceAll("-+", "-");
        return sanitized.substring(0, Math.min(sanitized.length(), 50));
    }
    

    private void createDirectoryIfNeeded() {
        try {
            Files.createDirectories(storageDir);
        } catch (IOException e) {
            log.error("Failed to create storage directory: {}", storageDir, e);
        }
    }
    

    private void loadExistingIssues() {
        log.info("FileIssueStorage ready (existing issues not loaded - cache starts empty)");
    }
    

    private int cleanupOldFiles(Duration olderThan) throws IOException {
        Instant cutoff = Instant.now().minus(olderThan);
        LocalDate cutoffDate = cutoff.atZone(ZoneId.systemDefault()).toLocalDate();
        
        int removed = 0;
        
        try (Stream<Path> dirs = Files.list(storageDir)) {
            for (Path dateDir : dirs.collect(Collectors.toList())) {
                if (Files.isDirectory(dateDir)) {
                    try {
                        LocalDate date = LocalDate.parse(dateDir.getFileName().toString());
                        if (date.isBefore(cutoffDate)) {
                            deleteDirectory(dateDir);
                            removed++;
                        }
                    } catch (java.time.format.DateTimeParseException e) {
                        log.debug("Skipping non-date directory during cleanup: {}",
                            dateDir.getFileName());
                    }
                }
            }
        }
        
        return removed;
    }
    

    private void deleteDirectory(Path dir) throws IOException {
        try (Stream<Path> files = Files.walk(dir)) {
            files.sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        log.error("Failed to delete: {}", path, e);
                    }
                });
        }
    }
    

    private void deleteAllFiles() throws IOException {
        try (Stream<Path> dirs = Files.list(storageDir)) {
            for (Path dateDir : dirs.collect(Collectors.toList())) {
                if (Files.isDirectory(dateDir)) {
                    deleteDirectory(dateDir);
                }
            }
        }
    }
}
