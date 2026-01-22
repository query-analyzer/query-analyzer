# Query Analyzer - Basic Example

A comprehensive example application demonstrating Query Analyzer's N+1 detection capabilities.

## Quick Start

```bash
# From the project root
cd query-analyzer-examples/example-basic

# Run the application
mvn spring-boot:run

# In another terminal, test an endpoint
curl http://localhost:8080/api/examples/bad/n-plus-one
```

Watch the console for Query Analyzer output!

## What's Included

### Entities

- **User** - Has a lazy-loaded `List<Order>` (OneToMany)
- **Order** - Belongs to a User (ManyToOne)

### Endpoints

#### BAD Examples (Trigger N+1 Detection)

| Endpoint | Description | Expected Detection |
|----------|-------------|-------------------|
| `GET /api/examples/bad/n-plus-one` | Classic N+1 with lazy loading | ERROR: N+1 Pattern |
| `GET /api/examples/bad/multiple-n-plus-one` | Multiple N+1 in one request | Multiple ERRORs |
| `GET /api/examples/bad/query-in-loop` | Explicit queries in loop | ERROR: N+1 Pattern |
| `GET /api/examples/bad/slow-query` | Simulated slow query | WARNING: Slow Query |
| `GET /api/examples/bad/everything-wrong` | All anti-patterns combined | Multiple Issues |

#### GOOD Examples (Optimized)

| Endpoint | Description | Expected Detection |
|----------|-------------|-------------------|
| `GET /api/examples/good/n-plus-one-fixed` | Uses JOIN FETCH | No Issues |
| `GET /api/examples/good/query-in-loop-fixed` | Uses batch loading | No Issues |
| `GET /api/examples/good/best-practices` | All optimizations applied | No Issues |

### Custom Extensions

- **DuplicateQueryDetector** - Custom detector for exact duplicate queries
- **FileReporter** - Custom reporter that writes to file (disabled by default)
- **UserService** - Service layer with BAD/GOOD method examples

## Running Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=NPlusOneDetectionTest

# Run endpoint tests
mvn test -Dtest=ExampleEndpointTest
```

## Test Examples

### Using @NoNPlusOne Annotation

```java
@Test
@NoNPlusOne
@Transactional(readOnly = true)
void testOptimizedQuery_ShouldPass() {
    List<User> users = userRepository.findAllWithOrders();
    
    // Access orders - already loaded via JOIN FETCH
    for (User user : users) {
        user.getOrders().size();
    }
    
    // Test passes - no N+1 detected
}

@Test
@NoNPlusOne(threshold = 5)
void testWithThreshold() {
    // Allows up to 5 repetitions before failing
}

@Test
@NoNPlusOne(minConfidence = 0.8)
void testWithHighConfidence() {
    // Only fails on high-confidence detections
}
```

## Configuration

The example uses these Query Analyzer settings (see `application.yml`):

```yaml
query-analyzer:
  enabled: true
  profile: BALANCED
  
  detection:
    n-plus-one: true
    slow-queries: true
    
  thresholds:
    info: 10
    warning: 50
    error: 200
    critical: 1000
    
  reporter:
    colors: true
    suggestions: true
    metrics: true
```

## Test Script

Run the test script to test all endpoints:

```bash
chmod +x test-examples.sh
./test-examples.sh
```

## H2 Console

Access the H2 database console at: http://localhost:8080/h2-console

- JDBC URL: `jdbc:h2:mem:testdb`
- Username: `sa`
- Password: (empty)

## Understanding the Output

### When N+1 is Detected

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

### When Code is Optimized

No console output when no issues are detected. With DEBUG logging enabled:

```
DEBUG i.q.s.service.QueryAnalysisOrchestrator  : Analyzing 1 queries for GET /api/examples/good/n-plus-one-fixed
DEBUG i.q.s.service.QueryAnalysisOrchestrator  : Metrics recorded: 1 queries, 0 issues, 1ms duration
DEBUG i.q.s.service.QueryAnalysisOrchestrator  : No performance issues detected
```
