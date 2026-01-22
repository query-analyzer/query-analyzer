# Configuration

## Quick Start

**Default (works for most apps):**
```yaml
query-analyzer:
  enabled: true
```

**With profile:**
```yaml
query-analyzer:
  profile: BALANCED  # STRICT, BALANCED, or LENIENT
```

---

## Profiles

| Profile | Use Case | Sensitivity |
|---------|----------|-------------|
| **STRICT** | Development, CI/CD | High (catches more, some false positives) |
| **BALANCED** | Most applications | Medium (good balance) |
| **LENIENT** | Production, batch jobs | Low (fewer false positives) |

### STRICT
```yaml
query-analyzer:
  profile: STRICT
```
- Min confidence: 0.3
- Min repetitions: 2
- Max queries: 1,000

### BALANCED (Default)
```yaml
query-analyzer:
  profile: BALANCED
```
- Min confidence: 0.5
- Min repetitions: 3
- Max queries: 5,000

### LENIENT
```yaml
query-analyzer:
  profile: LENIENT
```
- Min confidence: 0.7
- Min repetitions: 5
- Max queries: 10,000

---

## Common Configurations

### Development
```yaml
query-analyzer:
  profile: STRICT
  reporter:
    colors: true
    minimum-severity: INFO
```

### Production
```yaml
query-analyzer:
  profile: LENIENT
  reporter:
    colors: false
    minimum-severity: ERROR
```

### Disable Completely
```yaml
query-analyzer:
  enabled: false
```

---

## Query Plan Analysis

Analyzes database EXPLAIN output for deeper insights.

```yaml
query-analyzer:
  plan:
    enabled: true           # Enable/disable
    max-per-request: 3      # Max plans per request
    timeout-seconds: 2      # EXPLAIN timeout
    min-severity: ERROR     # Only analyze ERROR+
    max-per-minute: 60      # Rate limit
```

**Supported:** MySQL, PostgreSQL, H2

**Disable for high traffic:**
```yaml
query-analyzer:
  plan:
    enabled: false
```

---

## Detection Tuning

### Detection Mode
```yaml
query-analyzer:
  detection:
    mode: CONFIDENCE  # THRESHOLD, CONFIDENCE, or HYBRID
```

| Mode | Description | Best For |
|------|-------------|----------|
| THRESHOLD | Count-based (3+ repeated = N+1) | Unit tests, simple detection |
| CONFIDENCE | Score-based (stack + timing analysis) | Production (default) |
| HYBRID | Both must agree | Highest accuracy, fewest false positives |

### Override Profile Defaults
```yaml
query-analyzer:
  profile: BALANCED
  detection:
    min-confidence: 0.4  # More sensitive than BALANCED default
```

### Advanced Settings
```yaml
query-analyzer:
  detection:
    n-plus-one: true
    slow-queries: true
    advanced:
      min-repetitions: 3
      max-queries: 5000
```

---

## Reporter Settings

```yaml
query-analyzer:
  reporter:
    colors: true              # ANSI colors in output
    suggestions: true         # Show fix suggestions
    metrics: true             # Show timing metrics
    minimum-severity: INFO    # INFO, WARNING, ERROR, CRITICAL
```

---

## Slow Query Thresholds

```yaml
query-analyzer:
  thresholds:
    info: 50        # 50ms+  = INFO
    warning: 200    # 200ms+ = WARNING
    error: 500      # 500ms+ = ERROR
    critical: 2000  # 2s+    = CRITICAL
```

---

## Environment Variables

All properties work as environment variables:

```bash
QUERY_ANALYZER_ENABLED=true
QUERY_ANALYZER_PROFILE=BALANCED
QUERY_ANALYZER_PLAN_ENABLED=false
QUERY_ANALYZER_REPORTER_MINIMUM_SEVERITY=ERROR
```

---

## Metrics / Actuator

Expose metrics via Spring Boot Actuator endpoints.

```yaml
query-analyzer:
  metrics:
    enabled: true   # Enable /actuator/query-analyzer/* endpoints
```

When enabled, provides:
- `/actuator/query-analyzer/metrics` - JSON or Prometheus format
- `/actuator/query-analyzer/health` - Health check

See [API Reference](API_REFERENCE.md#actuator-endpoints) for details.

---

## Full Reference

```yaml
query-analyzer:
  enabled: true
  profile: BALANCED
  
  detection:
    n-plus-one: true
    slow-queries: true
    mode: CONFIDENCE    # THRESHOLD, CONFIDENCE, or HYBRID
    min-confidence: null  # Use profile default
    advanced:
      min-repetitions: 3
      max-queries: 5000
  
  plan:
    enabled: true
    max-per-request: 3
    timeout-seconds: 2
    min-severity: ERROR
    max-per-minute: 60
  
  thresholds:
    info: 50
    warning: 200
    error: 500
    critical: 2000
  
  reporter:
    colors: true
    suggestions: true
    metrics: true
    minimum-severity: INFO
  
  metrics:
    enabled: true       # Enable actuator endpoints
```

## See Also

- [Detection Modes](DETECTION_MODES.md) - THRESHOLD vs CONFIDENCE
