# Testing

Catch N+1 queries in tests with the `@NoNPlusOne` annotation.

## Basic Usage

```java
import io.queryanalyzer.test.NoNPlusOne;
import io.queryanalyzer.test.NoNPlusOneExtension;

@ExtendWith(NoNPlusOneExtension.class)
class UserServiceTest {

    @Test
    @NoNPlusOne
    void findAllUsers_shouldNotCauseNPlusOne() {
        userService.findAllWithOrders();
        // Test fails if 3+ identical queries detected
    }
}
```

## Options

### Custom Threshold

```java
@Test
@NoNPlusOne(threshold = 5)
void allowsUpTo4RepeatedQueries() {
    // Fails only if 5+ identical queries
}
```

### Ignore Tables

```java
@Test
@NoNPlusOne(ignore = {"audit_log", "metrics"})
void ignoresAuditQueries() {
    // Queries to audit_log and metrics are not counted
}
```

### Combined

```java
@Test
@NoNPlusOne(threshold = 5, ignore = {"audit_log"})
void customConfig() {
    // ...
}
```

## Test Failure Output

```
io.queryanalyzer.test.NPlusOneDetectedException: N+1 query pattern detected:

  10 repeated queries detected for 'orders'
    Location: UserService.findAll:47
    SQL: select o1_0.user_id,o1_0.id,o1_0.amount from orders o1_0 where o1_0.user_id=?
```

## Tips

1. **Use realistic data** - Create enough records to exceed the threshold
2. **Trigger lazy loading** - Actually access the collections in your test
3. **Ignore legitimate patterns** - Audit logs, polling queries

## Programmatic API

```java
@Test
void customAnalysis() {
    RequestContextHolder.start("/test", "GET");
    
    try {
        userService.findAll();
        
        List<QueryInfo> queries = QueryTracker.getQueries();
        // Custom assertions
        
    } finally {
        QueryTracker.clear();
    }
}
```
