package io.queryanalyzer.core.analyzer;

import io.queryanalyzer.core.analyzer.ImprovementEstimator.Confidence;
import io.queryanalyzer.core.analyzer.ImprovementEstimator.EstimateResult;
import io.queryanalyzer.core.model.QueryInfo;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ImprovementEstimatorTest {


    @Test
    void shouldReturnUnknownForEmptyQueries() {
        EstimateResult result = ImprovementEstimator.estimateNPlusOneImprovement(
            List.of(), 0);

        assertThat(result.getConfidence()).isEqualTo(Confidence.UNKNOWN);
        assertThat(result.getImprovementPercent()).isZero();
    }

    @Test
    void shouldReturnUnknownForSingleQuery() {
        List<QueryInfo> queries = List.of(createQuery("SELECT * FROM users", 10));

        EstimateResult result = ImprovementEstimator.estimateNPlusOneImprovement(
            queries, 10);

        assertThat(result.getConfidence()).isEqualTo(Confidence.UNKNOWN);
    }

    @Test
    void shouldReturnHighConfidenceForManyQueries() {
        List<QueryInfo> queries = createQueries(15, 10); // 15 queries, 10ms each

        EstimateResult result = ImprovementEstimator.estimateNPlusOneImprovement(
            queries, 150);

        assertThat(result.getConfidence()).isEqualTo(Confidence.HIGH);
        assertThat(result.getImprovementPercent()).isGreaterThan(80);
        assertThat(result.isReliable()).isTrue();
    }

    @Test
    void shouldReturnMediumConfidenceForModerateQueries() {
        List<QueryInfo> queries = createQueries(7, 10);

        EstimateResult result = ImprovementEstimator.estimateNPlusOneImprovement(
            queries, 70);

        assertThat(result.getConfidence()).isEqualTo(Confidence.MEDIUM);
        assertThat(result.isReliable()).isTrue();
    }

    @Test
    void shouldReturnLowConfidenceForFewQueries() {
        List<QueryInfo> queries = createQueries(3, 10);

        EstimateResult result = ImprovementEstimator.estimateNPlusOneImprovement(
            queries, 30);

        assertThat(result.getConfidence()).isEqualTo(Confidence.LOW);
        assertThat(result.isReliable()).isFalse();
    }

    @Test
    void shouldIncludeAssumptionsInResult() {
        List<QueryInfo> queries = createQueries(10, 10);

        EstimateResult result = ImprovementEstimator.estimateNPlusOneImprovement(
            queries, 100);

        assertThat(result.getAssumptions()).isNotEmpty();
        assertThat(result.getAssumptions()).anyMatch(a -> a.contains("batch"));
    }

    @Test
    void shouldIncludeExplanationInResult() {
        List<QueryInfo> queries = createQueries(10, 10);

        EstimateResult result = ImprovementEstimator.estimateNPlusOneImprovement(
            queries, 100);

        assertThat(result.getExplanation()).isNotEmpty();
        assertThat(result.getExplanation()).contains("10"); // Should mention query count
    }

    @Test
    void shouldCapImprovementAt95Percent() {
        // Even with 100 queries, shouldn't claim > 95% improvement
        List<QueryInfo> queries = createQueries(100, 10);

        EstimateResult result = ImprovementEstimator.estimateNPlusOneImprovement(
            queries, 1000);

        assertThat(result.getImprovementPercent()).isLessThanOrEqualTo(95);
    }


    @Test
    void shouldReturnUnknownForNullQuery() {
        EstimateResult result = ImprovementEstimator.estimateSlowQueryImprovement(null, 200);

        assertThat(result.getConfidence()).isEqualTo(Confidence.UNKNOWN);
    }

    @Test
    void shouldDetectSelectStarPattern() {
        QueryInfo query = createQuery("SELECT * FROM users WHERE id = 1", 500);

        EstimateResult result = ImprovementEstimator.estimateSlowQueryImprovement(query, 200);

        assertThat(result.getExplanation()).containsIgnoringCase("select *");
    }

    @Test
    void shouldDetectLeadingWildcardLike() {
        QueryInfo query = createQuery("SELECT name FROM users WHERE name LIKE '%smith'", 500);

        EstimateResult result = ImprovementEstimator.estimateSlowQueryImprovement(query, 200);

        assertThat(result.getExplanation()).containsIgnoringCase("like");
        assertThat(result.getImprovementPercent()).isGreaterThan(0);
    }

    @Test
    void shouldDetectMissingWhereClause() {
        QueryInfo query = createQuery("SELECT id, name FROM very_large_table", 5000);

        EstimateResult result = ImprovementEstimator.estimateSlowQueryImprovement(query, 200);

        assertThat(result.getExplanation()).containsIgnoringCase("where");
    }

    @Test
    void shouldDetectMultipleJoins() {
        QueryInfo query = createQuery(
            "SELECT * FROM a JOIN b ON a.id = b.a_id JOIN c ON b.id = c.b_id JOIN d ON c.id = d.c_id",
            1000);

        EstimateResult result = ImprovementEstimator.estimateSlowQueryImprovement(query, 200);

        assertThat(result.getExplanation()).containsIgnoringCase("join");
    }

    @Test
    void shouldReturnUnknownForSimpleOptimizedQuery() {
        QueryInfo query = createQuery(
            "SELECT id, name FROM users WHERE id = ? LIMIT 10", 
            250);

        EstimateResult result = ImprovementEstimator.estimateSlowQueryImprovement(query, 200);

        assertThat(result.getConfidence()).isEqualTo(Confidence.UNKNOWN);
        assertThat(result.getExplanation()).containsIgnoringCase("explain");
    }

    @Test
    void shouldAlwaysIncludeExplainAnalyzeRecommendation() {
        QueryInfo query = createQuery("SELECT * FROM users WHERE name LIKE '%test%'", 500);

        EstimateResult result = ImprovementEstimator.estimateSlowQueryImprovement(query, 200);

        assertThat(result.getAssumptions()).anyMatch(a -> 
            a.toLowerCase().contains("explain"));
    }

    @Test
    void shouldCapSlowQueryEstimateAt80Percent() {
        QueryInfo query = createQuery(
            "SELECT * FROM a JOIN b ON a.id = b.id JOIN c ON b.id = c.id " +
            "WHERE name LIKE '%test%' ORDER BY created_at", 
            5000);

        EstimateResult result = ImprovementEstimator.estimateSlowQueryImprovement(query, 200);

        assertThat(result.getImprovementPercent()).isLessThanOrEqualTo(80);
    }


    private List<QueryInfo> createQueries(int count, long executionTimeMs) {
        List<QueryInfo> queries = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            queries.add(createQuery("SELECT * FROM users WHERE id = " + i, executionTimeMs));
        }
        return queries;
    }

    private QueryInfo createQuery(String sql, long executionTimeMs) {
        return new QueryInfo(
            sql,
            sql,
            executionTimeMs,
            Instant.now(),
            null,
            "test-thread",
            null
        );
    }
}
