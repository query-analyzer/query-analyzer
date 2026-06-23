# Real-World Case Study: spring-petclinic

Query Analyzer was integrated into the canonical Spring reference application,
[spring-petclinic](https://github.com/spring-projects/spring-petclinic), to test
whether it finds genuine N+1 problems in third-party code with no manual setup.

**Result: three real N+1 problems detected, all at HIGH (100%) confidence, with a
single dependency and zero code changes to the application.**

## Setup

- App: `spring-projects/spring-petclinic` at commit `66747e3` (the last Spring Boot
  3.x commit - 3.5.6 - before the project moved to Spring Boot 4).
- Integration: added **one** dependency, nothing else:

```xml
<dependency>
  <groupId>io.github.query-analyzer</groupId>
  <artifactId>query-analyzer-spring-boot-starter</artifactId>
  <version>1.2.5</version>
</dependency>
```

On startup the tool auto-configured and wrapped the datasource:

```
QueryAnalyzerAutoConfiguration : Wrapping DataSource bean 'dataSource' with Query Analyzer proxy
QueryAnalyzerAutoConfiguration : N+1 query detector enabled (confidence threshold: 0.5)
```

The following pages were then requested: `GET /vets.html`, `GET /owners?lastName=`,
`GET /owners/1`, and `GET /owners/find` (the search form).

## Findings

petclinic maps `Owner.pets`, `Pet.visits`, and `Vet.specialties` with
`FetchType.EAGER`. Eager collections fetched across a *list* query are a classic
N+1 source: Hibernate issues one collection SELECT per parent row. Query Analyzer
caught all three:

| # | Endpoint | Collection | Repeated queries | Confidence | Pinpointed location |
|---|---|---|---|---|---|
| 1 | `GET /vets.html` | `vet_specialties` | 5 | HIGH (100%) | `CacheInterceptor.lambda$invoke$0:55` |
| 2 | `GET /owners` | `pets` | 5 | HIGH (100%) | `OwnerController.findPaginatedForOwnersLastName:130` |
| 3 | `GET /owners` | `visits` | 6 | HIGH (100%) | `OwnerController.findPaginatedForOwnersLastName:130` |

Example of the tool's console report (the long SQL line is wrapped here for page
width; otherwise reproduced as emitted):

```
  INFO | N+1 Query Detected

  Endpoint        GET /owners
  Location        OwnerController.findPaginatedForOwnersLastName:130

  Problem         5 repeated queries detected for 'pets'
                  Total: 0ms | Avg: 0ms per query

  Sample Query

      select p1_0.owner_id,p1_0.id,p1_0.birth_date,p1_0.name,t1_0.id,t1_0.name
      from pets p1_0 left join types t1_0 on t1_0.id=p1_0.type_id
      where p1_0.owner_id=? order by p1_0.name /* params: 1=5 */

  Suggestions     Confidence: HIGH (100%)
                  ORM/JDBC framework lazy loading detected in stack traces;
                  Queries executed in tight loop; Queries from same code location
```

Each report names the **endpoint**, the **exact source location** (e.g.
`OwnerController:130`), the offending **table/collection**, a **sample query**, and a
**confidence-scored explanation** - actionable enough to fix directly.

## Fix effectiveness (are the suggestions actionable?)

To check that the tool's advice actually works, we applied its own suggested fix -
`@BatchSize` - to the three eager collections (`Owner.pets`, `Pet.visits`,
`Vet.specialties`), rebuilt, and re-ran the exact same requests.

| State | N+1 problems reported by the tool |
|---|---|
| Before (stock petclinic) | 3 (vet_specialties, pets, visits) |
| After applying suggested `@BatchSize` | **0** |

The single annotation collapses each per-parent collection load into a batched
fetch, and re-running the tool confirms all three N+1 reports disappear. The
suggestions are therefore not just diagnostic but **directly actionable**: the tool
detects the problem, names a fix, and the named fix resolves what it detected.

## Why this matters for the paper

- Detection works on **unmodified third-party code**, not just crafted examples.
- One dependency, zero code changes - the integration story holds on a real app.
- The findings are **true positives on a well-known, well-reviewed reference app**,
  reinforcing that N+1 ships even in exemplary codebases.
- The tool's suggestions are **verified to fix** the problems it reports (3 -> 0).

## Reproduce

```bash
# Build & install the tool locally
cd query-analyzer && mvn clean install -DskipTests

# Check out the SB3 petclinic and add the one dependency (see snippet above)
git clone https://github.com/spring-projects/spring-petclinic.git
cd spring-petclinic && git checkout 66747e3
#   ...add the query-analyzer-spring-boot-starter dependency to pom.xml...

./mvnw -DskipTests package
java -jar target/spring-petclinic-*.jar &
curl -s localhost:8080/vets.html      > /dev/null
curl -s "localhost:8080/owners?lastName=" > /dev/null
# N+1 reports appear in the application log
```
