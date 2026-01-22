# Framework Support

Query Analyzer detects your ORM from stack traces and provides targeted hints.

## Detection

| Framework | Stack Trace Markers |
|-----------|---------------------|
| Hibernate | org.hibernate.*, jakarta.persistence.* |
| MyBatis | org.apache.ibatis.*, org.mybatis.* |
| jOOQ | org.jooq.* |
| Spring JDBC | org.springframework.jdbc.* |

## Suggestions

### Hibernate/JPA

```
Hibernate detected. Common fixes:
- Use JOIN FETCH in JPQL to load association eagerly
- Add @BatchSize(size=25) to the collection mapping
- Use @EntityGraph to specify fetch plan
```

### MyBatis

```
MyBatis detected. Common fixes:
- Replace nested select with nested result mapping
- Use JOIN query instead of separate selects
```

### jOOQ

```
jOOQ detected. Common fixes:
- Use multiset() for nested collections (jOOQ 3.14+)
- Use LEFT JOIN to fetch in single query
```

### Spring JDBC

```
Spring JDBC detected. Common fixes:
- Collect IDs and use IN clause for batch lookup
- Rewrite as JOIN query
```

### Unknown

```
Common N+1 fixes:
- Batch load: collect IDs, fetch with IN clause
- JOIN query: fetch parent and children together
