package io.queryanalyzer.benchmark.baseline;

import io.queryanalyzer.core.analyzer.SqlNormalizer;
import io.queryanalyzer.core.model.QueryInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Baseline detector modelling the count-based heuristic used by test-time N+1
 * tools such as QuickPerf's {@code @DisableSameSelectTypesWithDifferentParamValues}
 * and similar "same select executed N times" assertions.
 *
 * <p><b>This is a faithful re-implementation of the published algorithm, not the
 * third-party library itself.</b> It is included so the comparison runs on the exact
 * same captured trace as query-analyzer, eliminating differences in capture point as a
 * confound. The rule is deliberately simple, matching the documented behaviour:</p>
 *
 * <pre>
 *   Flag an N+1 if any SELECT "shape" (normalized SQL) executes &gt;= threshold times.
 * </pre>
 *
 * <p>It performs no false-positive suppression (pagination, streaming, batched IN) and
 * no confidence weighting - which is precisely the precision gap this benchmark
 * quantifies. The default threshold of 2 reflects "any repeated select of the same
 * type" as flagged by these tools out of the box.</p>
 */
public final class NaiveRepeatedSelectDetector {

    private final int threshold;

    public NaiveRepeatedSelectDetector() {
        this(2);
    }

    public NaiveRepeatedSelectDetector(int threshold) {
        this.threshold = threshold;
    }

    /** @return true if the trace is flagged as containing an N+1 pattern. */
    public boolean detects(List<QueryInfo> queries) {
        if (queries == null || queries.isEmpty()) {
            return false;
        }
        Map<String, Integer> selectCounts = new HashMap<>();
        for (QueryInfo q : queries) {
            if (!"SELECT".equals(SqlNormalizer.extractQueryType(q.getSql()))) {
                continue;
            }
            int count = selectCounts.merge(q.getNormalizedSql(), 1, Integer::sum);
            if (count >= threshold) {
                return true;
            }
        }
        return false;
    }
}
