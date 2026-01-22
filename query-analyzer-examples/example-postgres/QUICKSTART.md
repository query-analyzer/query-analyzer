# Quick Start - 3 Commands

## Start Everything

```bash
cd query-analyzer-examples/example-postgres
docker-compose up --build
```

Wait for: `Started PostgresExampleApplication`

## Test It

In another terminal:

```bash
# Trigger N+1 (bad)
curl http://localhost:8080/api/users

# No issues (good)
curl http://localhost:8080/api/users/optimized
```

Or run all tests:
```bash
cd query-analyzer-examples/example-postgres
./test.sh
```

## See the Results

Check the docker-compose logs. You should see output like:

```
--------------------------------------------------------------------------------

  INFO | N+1 Query Detected

  Endpoint        GET /api/users
  Location        UserController.convertToMap:207

  Problem         10 repeated queries detected for 'orders'
                  Total: 2ms | Avg: 0ms per query
                  Potential improvement: 80%

  Suggestions     Confidence: HIGH (100%)
                  ...

--------------------------------------------------------------------------------
```

## Stop

```bash
docker-compose down
```

That's it! You've seen Query Analyzer working with a real PostgreSQL database.
