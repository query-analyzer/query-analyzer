#!/usr/bin/env bash
#
# Reproduce every experiment reported in the Query Analyzer tool-demonstration
# paper with a single command:
#
#     ./run-all.sh
#
# Step 1 installs query-analyzer-core into the local Maven repository; the
# benchmark is a standalone Maven project that depends on it. Step 2 runs the
# whole benchmark suite.
#
# Reports are written to query-analyzer-benchmark/target/ :
#
#     benchmark-accuracy.csv   per-scenario verdict per detector        (RQ1)
#     benchmark-report.md      verdict table + precision/recall/F1      (RQ1)
#     benchmark-jplusone.md    live JPlusOne comparison                 (RQ1)
#
# The mode study / ablation / threshold sweep (RQ2) and the overhead and
# EXPLAIN measurements (RQ4) print their summaries to the console.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "==> [1/2] Installing query-analyzer-core into the local Maven repository"
cd "$ROOT"
./mvnw -q clean install -DskipTests

echo "==> [2/2] Running the benchmark suite"
cd "$ROOT/query-analyzer-benchmark"
"$ROOT/mvnw" test

echo
echo "==> Done. Reports written to query-analyzer-benchmark/target/ :"
ls -1 target/benchmark-*.csv target/benchmark-*.md 2>/dev/null || true
