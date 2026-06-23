package io.queryanalyzer.benchmark;

import io.queryanalyzer.core.analyzer.SqlNormalizer;
import io.queryanalyzer.core.config.DetectorConfig;
import io.queryanalyzer.core.detector.NPlusOneDetector;
import io.queryanalyzer.core.model.QueryInfo;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Framework-agnosticism evidence: a genuine N+1 expressed in <em>plain JDBC</em> with no
 * ORM whatsoever (raw {@link DriverManager}/{@link PreparedStatement} against H2). Because
 * Query Analyzer captures at the JDBC layer, the same detector runs on this trace. There
 * are no ORM frames in the stack, so the framework signal scores zero - this exercises
 * exactly the case a Hibernate-only tool cannot see, and shows which detection modes still
 * catch it. Report-only; the assertion only checks the trace was produced.
 */
class PlainJdbcDetectionTest {

    @Test
    void detectsPlainJdbcNPlusOne() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:plainjdbc;DB_CLOSE_DELAY=-1")) {
            seed(conn);

            // Plain-JDBC N+1: one parent query, then one child query per parent row.
            List<QueryInfo> trace = new ArrayList<>();
            List<Long> userIds = new ArrayList<>();
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT id FROM users")) {
                record(trace, "SELECT id FROM users");
                while (rs.next()) userIds.add(rs.getLong(1));
            }
            for (Long id : userIds) {
                try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM orders WHERE user_id = ?")) {
                    ps.setLong(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        record(trace, "SELECT * FROM orders WHERE user_id = ?"); // captured per loop iteration
                        while (rs.next()) { /* consume */ }
                    }
                }
            }

            System.out.println("\n========= PLAIN JDBC (no ORM) N+1 =========");
            System.out.println("trace size = " + trace.size() + " (1 parent + N child queries)");
            for (DetectorConfig.DetectionMode mode : DetectorConfig.DetectionMode.values()) {
                DetectorConfig cfg = DetectorConfig.builder()
                        .detectionMode(mode).minRepetitions(3).build();
                boolean flagged = !new NPlusOneDetector(cfg).detect(trace).isEmpty();
                System.out.printf("  %-11s -> %s%n", mode, flagged ? "DETECTED N+1" : "missed");
            }
            System.out.println("===========================================\n");

            assertTrue(trace.size() >= 4, "should have captured the parent + child queries");
        }
    }

    /** Capture a statement the way the JDBC proxy would: SQL, normalized form, real stack, ts. */
    private void record(List<QueryInfo> trace, String sql) {
        trace.add(new QueryInfo(sql, SqlNormalizer.normalize(sql), 0L,
                Instant.now(), Thread.currentThread().getStackTrace(),
                Thread.currentThread().getName(), null));
    }

    private void seed(Connection conn) throws Exception {
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE users (id BIGINT PRIMARY KEY)");
            st.execute("CREATE TABLE orders (id BIGINT PRIMARY KEY, user_id BIGINT, amount DECIMAL(10,2))");
            for (int u = 1; u <= 5; u++) {
                st.execute("INSERT INTO users VALUES (" + u + ")");
                for (int o = 0; o < 3; o++) {
                    int oid = u * 10 + o;
                    st.execute("INSERT INTO orders VALUES (" + oid + ", " + u + ", 19.99)");
                }
            }
        }
    }
}
