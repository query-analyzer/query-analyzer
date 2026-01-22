package io.queryanalyzer.core.plan.impl;

import io.queryanalyzer.core.plan.QueryPlanAnalyzer;
import io.queryanalyzer.core.plan.model.DatabaseType;
import io.queryanalyzer.core.plan.model.QueryPlanResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;


public class MySQLPlanAnalyzer implements QueryPlanAnalyzer {
    
    private static final Logger log = LoggerFactory.getLogger(MySQLPlanAnalyzer.class);
    
    private static final Pattern DANGEROUS_FUNCTIONS = Pattern.compile(
        "(?i)(sleep|benchmark|get_lock|release_lock|is_free_lock|" +
        "load_file|into\\s+outfile|into\\s+dumpfile)"
    );
    
    private static final Pattern MULTIPLE_STATEMENTS = Pattern.compile(";\\s*\\w");
    
    private static final int MAX_SQL_LENGTH = 10000;
    private static final int EXPLAIN_TIMEOUT_SECONDS = 2;
    
    @Override
    public DatabaseType getDatabaseType() {
        return DatabaseType.MYSQL;
    }
    
    @Override
    public QueryPlanResult analyze(Connection connection, String sql) {
        if (connection == null || sql == null || sql.trim().isEmpty()) {
            return null;
        }
        
        String validationError = validateSqlForExplain(sql);
        if (validationError != null) {
            log.debug("SQL not safe for EXPLAIN: {}", validationError);
            return null;
        }
        
        String analyzableSql = prepareForExplain(sql);
        
        try {
            return executeExplain(connection, analyzableSql);
        } catch (SQLException e) {
            log.debug("Failed to execute EXPLAIN for MySQL query: {}", e.getMessage());
            return null;
        }
    }
    

    private String validateSqlForExplain(String sql) {
        String trimmed = sql.trim();
        String upper = trimmed.toUpperCase();
        
        if (!upper.startsWith("SELECT")) {
            return "Only SELECT queries can be analyzed";
        }
        
        if (MULTIPLE_STATEMENTS.matcher(sql).find()) {
            return "Multiple statements detected";
        }
        
        if (DANGEROUS_FUNCTIONS.matcher(sql).find()) {
            return "Dangerous function detected";
        }
        
        if (sql.length() > MAX_SQL_LENGTH) {
            return String.format("SQL too long (%d chars, max %d)", sql.length(), MAX_SQL_LENGTH);
        }
        
        if (sql.contains("--") || sql.contains("/*")) {
            return "SQL comments not allowed in EXPLAIN";
        }
        
        return null;
    }
    

    private String prepareForExplain(String sql) {
        if (!sql.contains("?")) {
            return sql;
        }
        
        String prepared = sql.replaceAll("\\?", "1");
        log.trace("Prepared parameterized SQL for EXPLAIN");
        return prepared;
    }
    
    private QueryPlanResult executeExplain(Connection connection, String sql) throws SQLException {
        String explainSql = "EXPLAIN " + sql;
        
        try (Statement stmt = connection.createStatement()) {
            stmt.setQueryTimeout(EXPLAIN_TIMEOUT_SECONDS);
            
            try (ResultSet rs = stmt.executeQuery(explainSql)) {
            
                if (!rs.next()) {
                    return null;
                }
                
                String type = getStringOrNull(rs, "type");
                String key = getStringOrNull(rs, "key");
                long rows = rs.getLong("rows");
                String extra = getStringOrNull(rs, "Extra");
                
                boolean usesIndex = key != null && !key.isEmpty();
                boolean fullTableScan = "ALL".equalsIgnoreCase(type);
                
                List<String> recommendations = buildRecommendations(
                    type, key, rows, extra, sql
                );
                
                String rawPlan = formatRawPlan(type, key, rows, extra);
                
                return QueryPlanResult.builder()
                    .databaseType(DatabaseType.MYSQL)
                    .query(sql)
                    .usesIndex(usesIndex)
                    .indexName(key)
                    .fullTableScan(fullTableScan)
                    .estimatedRows(rows)
                    .estimatedCost(rows) // MySQL doesn't provide cost, use rows as proxy
                    .accessType(type)
                    .recommendations(recommendations)
                    .rawPlan(rawPlan)
                    .build();
            }
        }
    }
    
    private List<String> buildRecommendations(
        String type, 
        String key, 
        long rows, 
        String extra, 
        String sql) {
        
        List<String> recommendations = new ArrayList<>();
        
        if ("ALL".equalsIgnoreCase(type)) {
            recommendations.add("Full table scan detected");
            
            if (rows > 10000) {
                recommendations.add(String.format(
                    "Scanning %,d rows - performance may be impacted", rows));
            } else if (rows > 1000) {
                recommendations.add(String.format(
                    "Scanning %,d rows", rows));
            }
            
            // Generic suggestion (no specific column)
            recommendations.add("Query filters data - consider adding index on filtered columns");
        }
        
        if (key == null && rows > 1000) {
            recommendations.add(String.format(
                "No index used - %,d rows examined", rows));
        }
        
        if (extra != null && extra.contains("Using filesort")) {
            recommendations.add("Using filesort - ORDER BY requires sorting in memory");
        }
        
        if (extra != null && extra.contains("Using temporary")) {
            recommendations.add("Using temporary table - GROUP BY or DISTINCT requires temp storage");
        }
        
        if (extra != null && extra.contains("Using where")) {
            recommendations.add("Query applies WHERE filter after scan");
        }
        
        if ("const".equalsIgnoreCase(type)) {
            recommendations.add("Optimal: Single row lookup using PRIMARY KEY or UNIQUE index");
        } else if ("eq_ref".equalsIgnoreCase(type)) {
            recommendations.add("Optimal: One row per join using index");
        } else if ("ref".equalsIgnoreCase(type)) {
            recommendations.add("Good: Using non-unique index lookup");
        } else if ("range".equalsIgnoreCase(type)) {
            recommendations.add("Good: Using index for range scan");
        } else if ("index".equalsIgnoreCase(type)) {
            recommendations.add("Using index scan (better than full table scan)");
        }
        
        return recommendations;
    }
    
    private String formatRawPlan(String type, String key, long rows, String extra) {
        StringBuilder sb = new StringBuilder();
        sb.append("type=").append(type);
        sb.append(", key=").append(key != null ? key : "NULL");
        sb.append(", rows=").append(rows);
        if (extra != null) {
            sb.append(", extra=").append(extra);
        }
        return sb.toString();
    }
    
    private String getStringOrNull(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return (value != null && !value.isEmpty()) ? value : null;
    }
}
