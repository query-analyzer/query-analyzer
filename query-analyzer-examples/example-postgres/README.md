# Query Analyzer - PostgreSQL Example

Production-like example demonstrating Query Analyzer with PostgreSQL and Docker.

## Features

- Real PostgreSQL database
- Actual execution times (2-10ms vs 0ms with H2)
- Docker-based setup
- HIGH confidence scores (100%)
- Clean console output
- Multiple example endpoints

## Quick Start

```bash
docker-compose up --build
```

Wait for: `Started PostgresExampleApplication`

## Test

```bash
./test.sh
```

Or manually:

```bash
# BAD - N+1 problem
curl http://localhost:8080/api/users

# GOOD - Optimized
curl http://localhost:8080/api/users/optimized

# Metrics
curl http://localhost:8080/actuator/query-analyzer/metrics
```

## Expected Output

When you hit `/api/users` (the bad endpoint), you'll see:

```
--------------------------------------------------------------------------------

  INFO | N+1 Query Detected

  Endpoint        GET /api/users
  Location        UserController.convertToMap:207

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

When you hit `/api/users/optimized` (the good endpoint), you'll see only DEBUG logs:
```
DEBUG i.q.s.service.QueryAnalysisOrchestrator  : Analyzing 1 queries for GET /api/users/optimized
DEBUG i.q.s.service.QueryAnalysisOrchestrator  : Metrics recorded: 1 queries, 0 issues, 0ms duration
DEBUG i.q.s.service.QueryAnalysisOrchestrator  : No performance issues detected
```

## Endpoints

### Bad Examples (Demonstrate Problems)

| Endpoint | Issue Detected |
|----------|----------------|
| `/api/users` | N+1 on orders (HIGH confidence) |
| `/api/users/examples/bad/n-plus-one` | N+1 on orders (HIGH confidence) |
| `/api/users/examples/bad/multiple-n-plus-one` | N+1 on orders |
| `/api/users/examples/bad/query-in-loop` | N+1 on orders (MEDIUM confidence) |

### Good Examples (Best Practices)

| Endpoint | Result |
|----------|--------|
| `/api/users/optimized` | No issues (JOIN FETCH) |
| `/api/users/examples/good/n-plus-one-fixed` | No issues |
| `/api/users/examples/good/query-in-loop-fixed` | No issues (batch loading) |
| `/api/users/examples/good/best-practices` | No issues |

## Stop

```bash
docker-compose down
```
