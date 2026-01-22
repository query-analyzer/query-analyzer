# API Reference

Public APIs for Query Analyzer integration and extension.

---

## Core Classes

### QueryTracker

Thread-safe query tracking facade.

```java
import io.queryanalyzer.core.tracker.QueryTracker;

// Record a query
QueryTracker.recordQuery("SELECT * FROM users", 15);

// Check if tracking is active
boolean active = QueryTracker.isTracking();

// Get recorded queries
List<QueryInfo> queries = QueryTracker.getQueries();

// Clear and clean up
QueryTracker.clear();

// Enable/disable tracking
QueryTracker.setEnabled(true);
boolean enabled = QueryTracker.isEnabled();
```

### RequestContextHolder

Low-level ThreadLocal storage (used internally by QueryTracker).

```java
import io.queryanalyzer.core.context.RequestContextHolder;

// Start with request metadata
RequestContextHolder.start("/api/users", "GET");

// Or with user ID
RequestContextHolder.start("/api/users", "GET", "user123");

// Record query
RequestContextHolder.recordQuery(queryInfo);

// Get current context
RequestContext ctx = RequestContextHolder.get();

// Clean up
RequestContextHolder.clear();
```

---

## Data Classes

### QueryInfo

Represents a single query execution.

```java
public final class QueryInfo {
    String getSql();              // Original SQL
    String getNormalizedSql();    // Parameterized SQL
    long getExecutionTimeMs();    // Execution time
    Instant getTimestamp();       // When executed
    StackTraceElement[] getStackTrace();  // Filtered stack trace
    String getStackTraceString(); // Stack trace as string
    String getThreadName();       // Thread that executed query
    Map<String, Object> getMetadata();  // Additional metadata
}

// Using builder
QueryInfo query = QueryInfo.builder()
    .sql("SELECT * FROM users WHERE id = 1")
    .normalizedSql("select * from users where id = ?")
    .executionTimeMs(15)
    .timestamp(Instant.now())
    .threadName("http-nio-8080-exec-1")
    .build();
```

### QueryIssue

Represents a detected performance issue.

```java
public class QueryIssue {
    IssueType getType();           // N_PLUS_ONE, SLOW_QUERY
    Severity getSeverity();        // INFO, WARNING, ERROR, CRITICAL
    String getDescription();       // Human-readable description
    String getLocation();          // Code location
    String getEndpoint();          // HTTP endpoint
    String getSampleQuery();       // Example query
    List<String> getSuggestions(); // Fix suggestions
    QueryMetrics getMetrics();     // Performance metrics
    Instant getDetectedAt();       // Detection timestamp
    QueryPlanResult getPlanResult(); // Database plan analysis (if available)
}
```

### QueryMetrics

Performance statistics for an issue.

```java
public class QueryMetrics {
    long getExecutionTimeMs();           // Total time
    int getQueryCount();                 // Number of queries
    double getPotentialImprovementPercent(); // Estimated savings
}
```

### Severity

Issue severity levels.

```java
public enum Severity {
    INFO,      // Informational
    WARNING,   // Potential problem
    ERROR,     // Definite problem
    CRITICAL   // Severe problem
}
```

### IssueType

Types of detected issues.

```java
public enum IssueType {
    N_PLUS_ONE,  // Repeated query pattern
    SLOW_QUERY   // Slow execution
}
```

---

## Interfaces

### QueryDetector

Implement to create custom detectors.

```java
public interface QueryDetector {
    String getName();
    List<QueryIssue> detect(List<QueryInfo> queries);
}
```

**Example:**
```java
@Component
public class CartesianProductDetector implements QueryDetector {
    
    @Override
    public String getName() {
        return "cartesian-product";
    }
    
    @Override
    public List<QueryIssue> detect(List<QueryInfo> queries) {
        // Detection logic
        return issues;
    }
}
```

### QueryReporter

Implement to create custom reporters.

```java
public interface QueryReporter {
    void report(List<QueryIssue> issues);
}
```

**Example:**
```java
@Component
public class SlackReporter implements QueryReporter {
    
    @Override
    public void report(List<QueryIssue> issues) {
        issues.stream()
            .filter(i -> i.getSeverity() == Severity.ERROR)
            .forEach(i -> slackClient.send(formatIssue(i)));
    }
}
```

---

## Utility Classes

### SqlNormalizer

Normalizes SQL for pattern matching.

```java
import io.queryanalyzer.core.analyzer.SqlNormalizer;

String normalized = SqlNormalizer.normalize(
    "SELECT * FROM users WHERE id = 123 AND name = 'John'"
);
// Result: "select * from users where id = ? and name = ?"

// With metadata extraction
SqlNormalizer.NormalizationResult result = SqlNormalizer.normalizeWithMetadata(
    "SELECT * FROM users LIMIT 10 OFFSET 20"
);
result.getNormalizedSql();  // "select * from users limit 10 offset 20"
result.getLimit();          // 10
result.getOffset();         // 20
result.hasPagination();     // true

// Extract query type
String type = SqlNormalizer.extractQueryType("SELECT * FROM users");
// Result: "SELECT"
```

### StackTraceFilter

Filters stack traces to show application code.

```java
import io.queryanalyzer.core.analyzer.StackTraceFilter;

// Filter stack trace elements
StackTraceElement[] filtered = StackTraceFilter.filter(
    Thread.currentThread().getStackTrace()
);
// Removes: java.*, org.hibernate.*, org.springframework.*, etc.

// Find first application code location
String location = StackTraceFilter.findApplicationCode(
    Thread.currentThread().getStackTrace()
);
// Result: "UserService.getAllUsers:42"
```

---

## Test Support

### @NoNPlusOne

Annotation to fail tests on N+1 detection.

```java
import io.queryanalyzer.test.NoNPlusOne;
import io.queryanalyzer.test.NoNPlusOneExtension;

@ExtendWith(NoNPlusOneExtension.class)
class MyTest {

    @Test
    @NoNPlusOne
    void testNoNPlusOne() {
        // Test fails if N+1 detected
    }
    
    @Test
    @NoNPlusOne(threshold = 5, ignore = {"audit_log"})
    void testWithOptions() {
        // Custom threshold and ignored tables
    }
}
```

**Parameters:**
- `threshold` - Min repetitions to trigger (default: 3)
- `ignore` - Table names to ignore

---

## Configuration Properties

### QueryAnalyzerProperties

Spring Boot configuration binding.

```java
@ConfigurationProperties(prefix = "query-analyzer")
public class QueryAnalyzerProperties {
    private boolean enabled = true;
    private DetectionProfile profile = DetectionProfile.BALANCED;
    private Detection detection = new Detection();
    private PlanAnalysis plan = new PlanAnalysis();
    private Thresholds thresholds = new Thresholds();
    private Reporter reporter = new Reporter();
    private Metrics metrics = new Metrics();
}
```

See [Configuration Guide](CONFIGURATION.md) for YAML examples.

---

## Spring Beans

Auto-configured beans (can be overridden):

| Bean | Type | Purpose |
|------|------|---------|
| `nPlusOneDetector` | NPlusOneDetector | N+1 detection |
| `slowQueryDetector` | SlowQueryDetector | Slow query detection |
| `consoleReporter` | ConsoleReporter | Console output |
| `queryAnalysisOrchestrator` | QueryAnalysisOrchestrator | Coordinates analysis |
| `queryAnalysisFilter` | QueryAnalysisFilter | HTTP filter |

**Override example:**
```java
@Bean
public QueryReporter customReporter() {
    return new MyCustomReporter();
}
```

---

## Actuator Endpoints

Query Analyzer exposes metrics via Spring Boot Actuator at `/actuator/query-analyzer/`.

**Enable with:**
```yaml
query-analyzer:
  metrics:
    enabled: true
```

### GET /actuator/query-analyzer/metrics

Returns metrics in JSON or Prometheus format based on `Accept` header.

**JSON (default):**
```bash
curl http://localhost:8080/actuator/query-analyzer/metrics
```

```json
{
  "totalIssues": 5,
  "totalRequests": 12,
  "currentQueries": 11,
  "lastDurationMs": 8,
  "nPlusOneCount": 4,
  "slowQueryCount": 1,
  "infoCount": 4,
  "warningCount": 0,
  "errorCount": 1,
  "criticalCount": 0
}
```

**Prometheus format:**
```bash
curl -H "Accept: text/plain" http://localhost:8080/actuator/query-analyzer/metrics
```

```
# HELP query_analyzer_issues_total Total number of query issues detected
# TYPE query_analyzer_issues_total counter
query_analyzer_issues_total 5

# HELP query_analyzer_issues_by_type_total Total issues by type
# TYPE query_analyzer_issues_by_type_total counter
query_analyzer_issues_by_type_total{type="n_plus_one"} 4
query_analyzer_issues_by_type_total{type="slow_query"} 1

# HELP query_analyzer_issues_by_severity_total Total issues by severity
# TYPE query_analyzer_issues_by_severity_total counter
query_analyzer_issues_by_severity_total{severity="info"} 4
query_analyzer_issues_by_severity_total{severity="warning"} 0
query_analyzer_issues_by_severity_total{severity="error"} 1
query_analyzer_issues_by_severity_total{severity="critical"} 0

# HELP query_analyzer_requests_analyzed_total Total number of requests analyzed
# TYPE query_analyzer_requests_analyzed_total counter
query_analyzer_requests_analyzed_total 12

# HELP query_analyzer_queries_per_request Distribution of query counts per request
# TYPE query_analyzer_queries_per_request histogram
query_analyzer_queries_per_request_bucket{le="10"} 2
query_analyzer_queries_per_request_bucket{le="50"} 12
query_analyzer_queries_per_request_bucket{le="100"} 12
query_analyzer_queries_per_request_bucket{le="500"} 12
query_analyzer_queries_per_request_bucket{le="+Inf"} 12
query_analyzer_queries_per_request_count 12

# HELP query_analyzer_detection_duration_seconds Distribution of detection operation durations
# TYPE query_analyzer_detection_duration_seconds histogram
query_analyzer_detection_duration_seconds_bucket{le="0.001"} 0
query_analyzer_detection_duration_seconds_bucket{le="0.005"} 8
query_analyzer_detection_duration_seconds_bucket{le="0.010"} 11
query_analyzer_detection_duration_seconds_bucket{le="0.050"} 12
query_analyzer_detection_duration_seconds_bucket{le="+Inf"} 12
query_analyzer_detection_duration_seconds_count 12
```

### GET /actuator/query-analyzer/health

Health check endpoint.

```bash
curl http://localhost:8080/actuator/query-analyzer/health
```

```json
{
  "status": "UP",
  "requestsAnalyzed": 12
}
```

### Metrics Reference

| Metric | Type | Description |
|--------|------|-------------|
| `totalIssues` | counter | Total issues detected |
| `totalRequests` | counter | Total requests analyzed |
| `currentQueries` | gauge | Queries in last request |
| `lastDurationMs` | gauge | Last detection duration |
| `nPlusOneCount` | counter | N+1 issues detected |
| `slowQueryCount` | counter | Slow query issues detected |
| `infoCount` | counter | INFO severity issues |
| `warningCount` | counter | WARNING severity issues |
| `errorCount` | counter | ERROR severity issues |
| `criticalCount` | counter | CRITICAL severity issues |
