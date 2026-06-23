# query-analyzer Benchmark

A reproducible benchmark that quantifies **how query-analyzer compares to the
count-based N+1 heuristic** used by test-time detectors such as
[QuickPerf](https://github.com/quick-perf/quickperf)
(`@DisableSameSelectTypesWithDifferentParamValues`),
[spring-hibernate-query-utils](https://github.com/yannbriancon/spring-hibernate-query-utils),
and Hypersistence Utils' `SQLStatementCountValidator`.

It measures two things:

1. **Accuracy** - precision / recall / F1 on a labelled suite of realistic JPA
   workloads (`AccuracyBenchmarkTest`).
2. **Overhead** - per-request cost of capture + analysis (`OverheadBenchmarkTest`).

See also **[CASE_STUDY.md](CASE_STUDY.md)**: integrating Query Analyzer into the real
`spring-petclinic` app (one dependency, zero code changes) found **three genuine N+1
problems at HIGH confidence**, each pinpointed to an exact source location.

## Why this design is fair

The hard part of comparing N+1 tools is that each hooks the database at a
different point, so "the same workload" produces different inputs. This harness
removes that confound:

- It captures the SQL trace **once**, at the Hibernate `StatementInspector` level,
  using the **real call stack** (genuine `org.hibernate.collection` /
  `SimpleJpaRepository` frames). That is exactly the signal query-analyzer's
  CONFIDENCE mode relies on - a synthetic trace would unfairly weaken it.
- It then replays the **identical trace** to every detector.

The competitor is a faithful re-implementation of the published count-based
algorithm (`NaiveRepeatedSelectDetector`), **not** the third-party library binary,
so the only thing that differs between detectors is the algorithm - which is the
point of the comparison. (Wiring the actual third-party libraries as live
side-by-side runs is a possible follow-up; it does not change the captured input.)

## The labelled scenario suite

| Scenario | Ground truth | What it stresses |
|---|---|---|
| `classic_n_plus_one` (lazy collection) | **N+1** | recall on the canonical case |
| `n_plus_one_via_query_method` | **N+1** | recall on repository-driven N+1 |
| `join_fetch` (optimized) | clean | true negative |
| `pagination_loop` | clean | **precision** - same shape, growing offset |
| `streaming_poll` (timestamp) | clean | **precision** - steady-cadence polling |
| `batched_in_clause` | clean | true negative (single `IN (...)`) |
| `repeated_below_threshold` | clean | **precision** - 2 identical lookups |

The `pagination_loop`, `streaming_poll`, and `repeated_below_threshold` scenarios
are where count-based heuristics raise **false positives**; query-analyzer's
false-positive suppression and confidence weighting are meant to avoid them. The
benchmark reports the actual outcome either way.

## How to run

```bash
# 1. Build & install the library locally (publishes query-analyzer-core to ~/.m2)
cd query-analyzer
mvn clean install -DskipTests

# 2. Run the benchmark
cd query-analyzer-benchmark
mvn test
```

Outputs:

- `target/benchmark-accuracy.csv` - per-scenario verdict for each detector
- `target/benchmark-report.md` - verdict table + precision/recall/F1 per detector
- Console - both summaries are also printed by Surefire

If you'd rather use the published artifact than a local build, set
`<query-analyzer.version>` in `pom.xml` to the Maven Central version and skip step 1.

## Reading the results

`benchmark-report.md` ends with a metrics table per detector. The headline is the
**precision gap**: the naive counter flags legitimate-but-repetitive scenarios,
while query-analyzer does not. Recall on true N+1 is identical.

### Measured results (this machine, H2, Hibernate 6.3 / Spring Boot 3.2)

Accuracy across the 7 scenarios:

| Detector | TP | FP | TN | FN | Precision | Recall | F1 |
|---|---|---|---|---|---|---|---|
| QA-THRESHOLD | 2 | 0 | 5 | 0 | **1.00** | 1.00 | **1.00** |
| QA-CONFIDENCE | 2 | 0 | 5 | 0 | **1.00** | 1.00 | **1.00** |
| QA-HYBRID | 2 | 0 | 5 | 0 | **1.00** | 1.00 | **1.00** |
| Naive (QuickPerf-style) | 2 | 3 | 2 | 0 | 0.40 | 1.00 | 0.57 |

The naive counter false-positives on `pagination_loop`, `streaming_poll`, and
`repeated_below_threshold`. query-analyzer suppresses all three.

### Live competitor: Hypersistence Utils (real binary, same stack)

`HypersistenceComparisonTest` runs the actual
`io.hypersistence:hypersistence-utils-hibernate-63:3.15.2` binary
(`SQLStatementCountValidator`) on the identical Spring Boot 3.2 / Hibernate 6.3
stack. (QuickPerf is not run live: its last release, 1.1.0/2021, supports only
Spring Boot 1/2 - see the count-based baseline above for its documented rule.)

| Scenario | Ground truth | Hypersistence SELECT count | query-analyzer (automatic) |
|---|---|---|---|
| classic_n_plus_one | N+1 | 6 | FLAG OK |
| n_plus_one_via_query_method | N+1 | 6 | FLAG OK |
| join_fetch | clean | 1 | pass OK |
| pagination_loop | clean | 5 | pass OK |
| streaming_poll | clean | 5 | pass OK |
| batched_in_clause | clean | 1 | pass OK |
| repeated_below_threshold | clean | 2 | pass OK |

The honest framing: Hypersistence is a **manual statement counter** - a developer
who hardcodes the right `assertSelectCount(n)` per test will not get false
positives. The differentiation is that (1) it needs a human-specified expected
count for *every* test, (2) a raw count cannot separate an N+1 (6 SELECTs) from
legitimate pagination (5 SELECTs) on its own, and (3) it runs only in tests.
query-analyzer classifies every scenario **automatically**, with zero per-test
configuration, and also runs in production. The test additionally asserts the real
binary behaves as documented (a correct `assertSelectCount(1)` passes; an N+1 makes
it throw).

Overhead (coarse wall-clock; use deltas, not absolutes):

| Component | Cost |
|---|---|
| Capture hook | below measurement noise (~0 ms/req) |
| Post-request analysis | ~0.28 ms/req (6-query trace) |

This supports the project's "<3 ms / <1%" claim with headroom.

> **Note on the pagination fix.** The first run of this benchmark exposed a real
> bug: query-analyzer's pagination suppression keyed on a *literal* `OFFSET n`, but
> Hibernate emits offsets as bind parameters (`offset ? rows fetch first ? rows
> only`), so it false-positived on `pagination_loop` (F1 was 0.80). The fix
> (`isPaginationPattern` in `NPlusOneDetector`) treats repeated same-shape reads as
> pagination when every query carries an `OFFSET` clause, while still flagging a
> bare repeated `LIMIT n` (a genuine N+1). All 245 core unit tests still pass.
> This is exactly the kind of finding the benchmark is meant to catch before review.

## Honest caveats (read before citing numbers)

- This is a **self-contained harness**, not yet a peer-reviewed result. Run it and
  use the numbers it actually produces on your machine.
- The competitor row is a faithful re-implementation of the published count-based
  algorithm, not the third-party binary. For a camera-ready paper, consider also
  wiring the actual QuickPerf/spring-hibernate-query-utils libraries as live runs
  (they will capture at a different point but should agree on these scenarios).
- The overhead numbers are coarse wall-clock estimates on in-memory H2. Use the
  deltas (capture vs. baseline), not the absolute milliseconds. For paper-grade
  overhead numbers, port `OverheadBenchmarkTest` to JMH.
- Slow-query detection depends on per-statement execution time, which the
  `StatementInspector` capture point does not provide; it is therefore excluded
  from the accuracy suite and should be benchmarked separately.
