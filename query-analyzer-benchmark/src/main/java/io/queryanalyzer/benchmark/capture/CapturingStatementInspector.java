package io.queryanalyzer.benchmark.capture;

import io.queryanalyzer.core.analyzer.SqlNormalizer;
import io.queryanalyzer.core.model.QueryInfo;
import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.time.Instant;

/**
 * Hibernate {@link StatementInspector} that records every SQL statement Hibernate
 * prepares, together with the <em>real</em> call stack at that moment.
 *
 * <p>Using Hibernate's own inspector (rather than a synthetic trace) means the stack
 * frames contain genuine Hibernate internals - {@code org.hibernate.collection},
 * {@code PersistentBag}, {@code SimpleJpaRepository}, etc. - which is exactly what
 * query-analyzer's CONFIDENCE mode inspects. A synthetic trace would unfairly starve
 * the confidence model of its strongest signal.</p>
 *
 * <p>Registered via {@code spring.jpa.properties.hibernate.session_factory.statement_inspector}.
 * Hibernate instantiates it with a no-arg constructor, so the sink is a static
 * thread-local ({@link CapturedQueries}).</p>
 *
 * <p>Note: {@code inspect} is invoked at statement-preparation time, so per-statement
 * execution time is not available here and is recorded as 0. N+1 detection is
 * count/pattern/timing based and does not depend on per-statement duration; slow-query
 * detection (which does) is therefore out of scope for this accuracy harness and is
 * measured separately.</p>
 */
public final class CapturingStatementInspector implements StatementInspector {

    @Override
    public String inspect(String sql) {
        try {
            if (sql != null && !sql.isBlank() && CapturedQueries.isActive()) {
                String normalized = SqlNormalizer.normalize(sql);
                if (normalized == null || normalized.isBlank()) {
                    normalized = sql.toLowerCase().trim();
                }
                QueryInfo info = new QueryInfo(
                        sql,
                        normalized,
                        0L,
                        Instant.now(),
                        Thread.currentThread().getStackTrace(),
                        Thread.currentThread().getName(),
                        null);
                CapturedQueries.add(info);
            }
        } catch (RuntimeException ignored) {
            // The inspector must never interfere with the workload being measured.
        }
        return sql;
    }
}
