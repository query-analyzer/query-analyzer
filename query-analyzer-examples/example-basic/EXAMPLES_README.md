# Query Analyzer Examples

This module contains working examples demonstrating Query Analyzer's capabilities.

## Quick Start

```bash
cd query-analyzer-examples/example-basic
mvn spring-boot:run
```

Then visit: http://localhost:8080

---

## Available Examples

###  BAD Examples (Demonstrate Issues)

#### 1. Classic N+1 Problem
**Endpoint:** `GET /api/examples/bad/n-plus-one`

Loads users, then queries orders for each user separately.

**Expected Console Output:**
```
--------------------------------------------------------------------------------

  INFO | N+1 Query Detected

  Endpoint        GET /api/examples/bad/n-plus-one
  Location        ExamplesController.nPlusOneBad:48

  Problem         10 repeated queries detected for 'orders'
                  Total: 1ms | Avg: 0ms per query
                  Potential improvement: 80%

  Sample Query

      select o1_0.user_id,o1_0.id,o1_0.amount,o1_0.order_date,o1_0.product_name 
      from orders o1_0 where o1_0.user_id=? /* params: 1=1 */

  Suggestions     Confidence: HIGH (100%)
                  ORM/JDBC framework lazy loading detected in stack traces; 
                  Queries executed in tight loop; Queries from same code location

                  Relationship: users -> orders (via user_id) (100% confidence)

--------------------------------------------------------------------------------
```

**Test:**
```bash
curl http://localhost:8080/api/examples/bad/n-plus-one
```

---

#### 2. N+1 with Nested Iteration
**Endpoint:** `GET /api/examples/bad/multiple-n-plus-one`

Demonstrates N+1 with nested iteration over lazy collections.

> **Note:** This triggers ONE N+1 pattern (for orders). Iterating over the same lazy collection multiple times doesn't create additional queries because Hibernate caches the loaded collection. To trigger multiple N+1 patterns, you would need lazy associations to different tables.

**Expected Console Output:**
```
--------------------------------------------------------------------------------

  INFO | N+1 Query Detected

  Endpoint        GET /api/examples/bad/multiple-n-plus-one
  Location        ExamplesController.multipleNPlusOne:XX

  Problem         10 repeated queries detected for 'orders'
                  Total: 0ms | Avg: 0ms per query

  Sample Query

      select o1_0.user_id,o1_0.id,o1_0.amount,o1_0.order_date,o1_0.product_name 
      from orders o1_0 where o1_0.user_id=? /* params: 1=1 */

  Suggestions     Confidence: HIGH (100%)
                  ...

--------------------------------------------------------------------------------
```

---

#### 3. Slow Query
**Endpoint:** `GET /api/examples/bad/slow-query`

Query that takes too long to execute (uses H2's SLEEP function).

**Expected Console Output:**
```
--------------------------------------------------------------------------------

  ERROR | Slow Query

  Endpoint        GET /api/examples/bad/slow-query
  Location        ExamplesController.slowQuery:114

  Problem         Query took 510ms (threshold: 50ms)
                  Total: 510ms

  Sample Query

      CALL SLEEP(500)

  Suggestions     Run EXPLAIN ANALYZE to understand query execution plan
                  Check if appropriate indexes exist on queried columns

--------------------------------------------------------------------------------
```

---

#### 4. Query in Loop
**Endpoint:** `GET /api/examples/bad/query-in-loop`

Executing repository queries inside a loop - classic anti-pattern.

**Expected Console Output:**
```
--------------------------------------------------------------------------------

  INFO | N+1 Query Detected

  Endpoint        GET /api/examples/bad/query-in-loop
  Location        ExamplesController.queryInLoop:XX

  Problem         10 repeated queries detected for 'orders'
                  Total: 1ms | Avg: 0ms per query
                  Potential improvement: 80%

  Sample Query

      select o1_0.id,o1_0.amount,o1_0.order_date,o1_0.product_name,o1_0.user_id 
      from orders o1_0 where o1_0.user_id=? /* params: 1=1 */

  Suggestions     Confidence: MEDIUM (85%)
                  Some framework indicators present; Queries executed in tight loop; 
                  Queries from same code location

                  Relationship: users -> orders (via user_id) (100% confidence)

                  Spring JDBC detected. Common fixes:
                  - Collect IDs and use IN clause for batch lookup
                  - Rewrite as JOIN query

--------------------------------------------------------------------------------
```

---

#### 5. Everything Wrong
**Endpoint:** `GET /api/examples/bad/everything-wrong`

Combines multiple anti-patterns to trigger multiple detected issues.

**Expected Console Output:**
```
--------------------------------------------------------------------------------

  ERROR | Slow Query

  Endpoint        GET /api/examples/bad/everything-wrong
  Location        ExamplesController.everythingWrong:XX

  Problem         Query took 200ms (threshold: 50ms)
                  Total: 200ms

  Sample Query

      CALL SLEEP(200)

  Suggestions     Run EXPLAIN ANALYZE to understand query execution plan
                  Check if appropriate indexes exist on queried columns

--------------------------------------------------------------------------------

--------------------------------------------------------------------------------

  INFO | N+1 Query Detected

  Endpoint        GET /api/examples/bad/everything-wrong
  Location        ExamplesController.everythingWrong:XX

  Problem         10 repeated queries detected for 'orders'
                  Total: 0ms | Avg: 0ms per query

  Sample Query

      select o1_0.user_id,o1_0.id,o1_0.amount,o1_0.order_date,o1_0.product_name 
      from orders o1_0 where o1_0.user_id=? /* params: 1=1 */

  Suggestions     Confidence: HIGH (100%)
                  ...

--------------------------------------------------------------------------------
```

---

###  GOOD Examples (Best Practices)

#### 6. N+1 Fixed with JOIN FETCH
**Endpoint:** `GET /api/examples/good/n-plus-one-fixed`

Eliminates N+1 by loading everything in one query using JOIN FETCH.

**Expected Console Output:**
No output in console (issues are only printed when detected). You'll see DEBUG logs if enabled:
```
DEBUG i.q.s.service.QueryAnalysisOrchestrator  : Analyzing 1 queries for GET /api/examples/good/n-plus-one-fixed
DEBUG i.q.s.service.QueryAnalysisOrchestrator  : Metrics recorded: 1 queries, 0 issues, 1ms duration
DEBUG i.q.s.service.QueryAnalysisOrchestrator  : No performance issues detected
```

**Test:**
```bash
curl http://localhost:8080/api/examples/good/n-plus-one-fixed
```

---

#### 7. Query in Loop Fixed
**Endpoint:** `GET /api/examples/good/query-in-loop-fixed`

Batch loads data using IN clause instead of querying in loop.

**Expected Console Output:**
No output in console. DEBUG logs will show:
```
DEBUG i.q.s.service.QueryAnalysisOrchestrator  : Analyzing 2 queries for GET /api/examples/good/query-in-loop-fixed
DEBUG i.q.s.service.QueryAnalysisOrchestrator  : No performance issues detected
```

---

#### 8. Best Practices
**Endpoint:** `GET /api/examples/good/best-practices`

Demonstrates all best practices:
- JOIN FETCH for associations
- Process data in memory
- No N+1 queries
- No slow queries

**Expected Console Output:**
No output in console when no issues are detected.

---

###  Test Examples

#### 9. Parameterized Query
**Endpoint:** `GET /api/examples/parameterized/{id}`

Tests that plan analysis handles parameterized queries (?) correctly.

**Test:**
```bash
curl http://localhost:8080/api/examples/parameterized/1
```

---

#### 10. Rate Limiting Test
**Endpoint:** `GET /api/examples/test/rate-limit`

Generate many errors to test rate limiting.

**Test:**
```bash
# Make 70 requests quickly (default limit is 60/min)
for i in {1..70}; do 
  curl http://localhost:8080/api/examples/test/rate-limit &
done
wait

# Check console for:
# [DEBUG] Query plan analysis rate limit exceeded, skipping
```

---


## How to Use These Examples

### 1. Learning

Run the BAD examples first to see what Query Analyzer detects:
```bash
curl http://localhost:8080/api/examples/bad/n-plus-one
```

Watch the console output carefully.

Then run the GOOD examples to see the difference:
```bash
curl http://localhost:8080/api/examples/good/n-plus-one-fixed
```

---

### 2. Testing Configuration

Modify `application.yml` to test different configurations:

```yaml
query-analyzer:
  enabled: true
  profile: BALANCED
  
  plan:
    enabled: true
    max-per-request: 3
    timeout-seconds: 2
    min-severity: ERROR
    max-per-minute: 60
```

Then re-run examples to see different behavior.

---

### 3. Performance Testing

Use Apache Bench or similar to load test:

```bash
# Test rate limiting
ab -n 100 -c 10 http://localhost:8080/api/examples/test/rate-limit

# Test under load
ab -n 1000 -c 10 http://localhost:8080/api/examples/bad/n-plus-one

# Verify no crashes, no memory leaks
```

---

### 4. Integration Testing

Run examples as integration tests:

```bash
# Start application
mvn spring-boot:run

# In another terminal, test endpoints
./test-examples.sh
```

---

## Example Output Explained

### Console Output Structure

Query Analyzer outputs issues in a clean, structured format:

```
--------------------------------------------------------------------------------

  INFO | N+1 Query Detected

  Endpoint        GET /api/users
  Location        UserController.convertToDTO:53

  Problem         10 repeated queries detected for 'orders'
                  Total: 5ms | Avg: 0ms per query
                  Potential improvement: 80%

  Sample Query

      select o1_0.user_id,o1_0.id,o1_0.amount,o1_0.order_date,o1_0.product_name 
      from orders o1_0 where o1_0.user_id=? /* params: 1=1 */

  Suggestions     Confidence: HIGH (100%)
                  ORM/JDBC framework lazy loading detected in stack traces; 
                  Queries executed in tight loop; Queries from same code location

                  Relationship: users -> orders (via user_id) (100% confidence)

--------------------------------------------------------------------------------
```

### Output Fields Explained

| Field | Description |
|-------|-------------|
| **Severity** | INFO, WARNING, ERROR, or CRITICAL |
| **Issue Type** | N+1 Query Detected, Slow Query Detected, etc. |
| **Endpoint** | HTTP method and path that triggered the issue |
| **Location** | Class.method:line where the queries originated |
| **Problem** | Description including query count, timing, and improvement estimate |
| **Sample Query** | One of the repeated queries with parameter values |
| **Suggestions** | Confidence score and framework-specific fix recommendations |

---

## Customizing Examples

### Add Your Own Example

1. Create new endpoint in `ExamplesController`
2. Implement your use case
3. Add documentation
4. Test it

Example:
```java
@GetMapping("/custom/my-example")
public ResponseEntity<?> myExample() {
    // Your code here
    return ResponseEntity.ok("test");
}
```

---

### Create Custom Detector

See `DuplicateQueryDetector.java` for an example of a custom detector.

```java
@Component
public class MyCustomDetector implements QueryDetector {
    
    @Override
    public String getName() {
        return "My Custom Detector";
    }
    
    @Override
    public List<QueryIssue> detect(List<QueryInfo> queries) {
        // Your detection logic
        return issues;
    }
}
```

Spring Boot will automatically pick it up!

---

## Troubleshooting

### No Output in Console

**Problem:** Running examples but seeing no Query Analyzer output.

**Solutions:**
1. Check `application.yml` - ensure `enabled: true`
2. Check log level - set to DEBUG if needed
3. Verify examples are actually triggering queries
4. Check if N+1 threshold is too high

---

### Too Much Output

**Problem:** Console flooded with Query Analyzer output.

**Solutions:**
1. Increase severity threshold:
   ```yaml
   query-analyzer:
     reporter:
       minimum-severity: ERROR
   ```

2. Disable specific detectors:
   ```yaml
   query-analyzer:
     detection:
       slow-queries: false
   ```

---

### Rate Limiting Kicking In

**Problem:** Seeing "rate limit exceeded" messages.

**Solutions:**
1. This is expected behavior when testing!
2. Increase limit:
   ```yaml
   query-analyzer:
     plan:
       max-per-minute: 120
   ```

3. Or disable plan analysis:
   ```yaml
   query-analyzer:
     plan:
       enabled: false
   ```

