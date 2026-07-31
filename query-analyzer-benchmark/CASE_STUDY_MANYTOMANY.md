# Second Real-World Case Study: a third-party many-to-many app

To check that Query Analyzer finds N+1s in unrelated third-party code (not just
spring-petclinic), it was applied to a community Spring Boot 3 sample with a
many-to-many mapping:
[`bezkoder/spring-boot-many-to-many`](https://github.com/bezkoder/spring-boot-many-to-many)
(Spring Boot 3.1, Hibernate 6, JPA).

**Result: one real N+1 detected at HIGH (100%) confidence, with one dependency and
no application code changes.** Unlike petclinic's controller-loop N+1, this one
fires during JSON *serialization* of the response — a distinct manifestation.

## The N+1

`Tutorial` has `@ManyToMany(fetch = LAZY)` to `Tag`. `GET /api/tutorials` calls
`findAll()` (one query) and returns the list; Jackson then serializes each
tutorial, touching the lazy `tags` collection and issuing one query per row.

## Integration (one dependency, no code changes)

Add the starter:

```xml
<dependency>
  <groupId>io.github.query-analyzer</groupId>
  <artifactId>query-analyzer-spring-boot-starter</artifactId>
  <version>1.2.8</version>
</dependency>
```

The sample ships configured for MySQL; to run it standalone the datasource was
pointed at in-memory H2 (configuration only, no code change):

```properties
spring.datasource.url=jdbc:h2:mem:m2mdb;DB_CLOSE_DELAY=-1
spring.datasource.driver-class-name=org.h2.Driver
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect
spring.jpa.hibernate.ddl-auto=create
```

with `com.h2database:h2` (runtime) replacing `mysql-connector-j`, and five
tutorials each carrying tags seeded via `src/main/resources/import.sql`.

## Observed report

```
N+1 Query Detected
  Endpoint     GET /api/tutorials
  Location     AbstractJackson2HttpMessageConverter.writeInternal:483
  Problem      5 repeated queries detected for 'tutorial_tags'
  Sample Query select t1_0.tutorial_id, t1_1.id, t1_1.name
               from tutorial_tags t1_0 join tags t1_1 on t1_1.id=t1_0.tag_id
               where t1_0.tutorial_id=?
  Suggestions  Confidence: HIGH (100%)
               ORM/JDBC framework lazy loading detected in stack traces;
               Queries executed in tight loop; Queries from same code location
               Relationship: tutorials -> tutorial_tags (via tutorial_id)
```

Hibernate's own log confirms it: one `select ... from tutorials` followed by five
identical `select ... from tutorial_tags ... where tutorial_id=?` statements.

## Reproduce

```bash
# Build & install the tool locally (publishes to ~/.m2), or use Maven Central 1.2.7
cd query-analyzer && mvn clean install -DskipTests

git clone https://github.com/bezkoder/spring-boot-many-to-many.git
cd spring-boot-many-to-many
#   ...add the starter dependency; swap mysql-connector-j -> com.h2database:h2;
#   point application.properties at H2 (snippet above); seed import.sql...
mvn -DskipTests package
java -jar target/spring-boot-many-to-many-*.jar &
curl -s localhost:8080/api/tutorials > /dev/null
# the N+1 report appears in the application log
```

## Why this matters for the paper

- A **second** real, third-party application, independent of petclinic.
- A **different** N+1 manifestation (serialization-time, via a many-to-many),
  showing detection is not specific to one code shape.
- Still one dependency, no code changes — the integration story holds on a
  second app.
