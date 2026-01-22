# Usage Examples

## What Query Analyzer Does

Query Analyzer **detects** N+1 problems and tells you:
- What the problem is (repeated queries)
- Where it's happening (code location)
- What the impact is (time wasted)
- What approach to fix it (framework-specific hints)

It does NOT generate code. You implement the fix.

## Example Output

When N+1 is detected:

```
--------------------------------------------------------------------------------

  INFO | N+1 Query Detected

  Endpoint        GET /api/users
  Location        UserController.getAllUsers:48

  Problem         10 repeated queries detected for 'orders'
                  Total: 2ms | Avg: 0ms per query
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

When no issues are detected, there is no console output. Enable DEBUG logging to see:
```
DEBUG i.q.s.service.QueryAnalysisOrchestrator  : Analyzing 1 queries for GET /api/users
DEBUG i.q.s.service.QueryAnalysisOrchestrator  : No performance issues detected
```

## Common Scenarios

### Lazy Loading in a Loop

**Problem**: Accessing a lazy collection for each entity in a loop.

**What you'll see**: "N repeated queries detected for [table]"

**Fix direction**: Use eager fetching (JOIN FETCH, @EntityGraph) or batch loading (@BatchSize).

### Parent-Child Relationships

**Problem**: Loading parent entities, then loading children one by one.

**What you'll see**: Detection with inferred relationship info.

**Fix direction**: Fetch parent and children together with a JOIN.

### Batch Processing

**Problem**: Processing items one at a time instead of in batches.

**What you'll see**: High query count with repeated pattern.

**Fix direction**: Collect IDs, fetch with IN clause.

## See Also

- [Framework Support](FRAMEWORKS.md) - Framework-specific hints
- [Configuration](CONFIGURATION.md) - Detection tuning
