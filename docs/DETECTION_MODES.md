# Detection Modes

Query Analyzer uses different detection algorithms internally. The mode is determined by your profile and configuration.

## How Detection Works

### THRESHOLD Detection

Count-based. If 3+ identical queries execute, it's flagged as N+1.

Used when:
- Running tests with `@NoNPlusOne`
- Profile has low min-confidence

### CONFIDENCE Detection

Score-based using multiple signals:
- Stack trace similarity (same code location?)
- Timing patterns (rapid succession?)
- Query structure (parameterized WHERE?)

Used when:
- Profile has higher min-confidence (BALANCED, LENIENT)

### HYBRID Detection

Both count AND confidence must agree. Only flags when:
1. Query count exceeds threshold
2. AND confidence score exceeds minimum

Used internally for highest accuracy.

## Profile Behavior

| Profile | Min Confidence | Min Repetitions | Behavior |
|---------|----------------|-----------------|----------|
| STRICT | 0.3 | 2 | More sensitive, catches more |
| BALANCED | 0.5 | 3 | Good balance |
| LENIENT | 0.7 | 5 | Conservative, fewer false positives |

## Configuration

You don't configure the mode directly. Instead, use profiles:

```yaml
query-analyzer:
  profile: BALANCED
```

Or override specific values:

```yaml
query-analyzer:
  profile: BALANCED
  detection:
    min-confidence: 0.4  # More sensitive than default
    advanced:
      min-repetitions: 5  # Less sensitive
```

## @NoNPlusOne in Tests

The test annotation uses simple THRESHOLD detection for predictable results:

```java
@NoNPlusOne(threshold = 3)  // Fails if 3+ repeated queries
```
