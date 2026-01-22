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


public class H2PlanAnalyzer implements QueryPlanAnalyzer {
    
    private static final Logger log = LoggerFactory.getLogger(H2PlanAnalyzer.class);
    
    private static final Pattern MULTIPLE_STATEMENTS = Pattern.compile(";\\s*\\w");
    private static final int MAX_SQL_LENGTH = 10000;
    private static final int EXPLAIN_TIMEOUT_SECONDS = 2;
    
    @Override
    public DatabaseType getDatabaseType() {
        return DatabaseType.H2;
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
            log.debug("Failed to execute EXPLAIN for H2 query: {}", e.getMessage());
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
        
        // Replace ? with '1' (quoted) - safer for both numeric and string columns
        // This is a best-effort replacement; complex cases may still fail
        String prepared = sql.replaceAll("\\?", "'1'");
        log.trace("Prepared parameterized SQL for EXPLAIN (replaced ? with '1')");
        return prepared;
    }
    
    private QueryPlanResult executeExplain(Connection connection, String sql) throws SQLException {
        String explainSql = "EXPLAIN " + sql;
        
        StringBuilder rawPlan = new StringBuilder();
        boolean usesIndex = false;
        boolean fullTableScan = false;
        
        try (Statement stmt = connection.createStatement()) {
            stmt.setQueryTimeout(EXPLAIN_TIMEOUT_SECONDS);
            
            try (ResultSet rs = stmt.executeQuery(explainSql)) {
            
                while (rs.next()) {
                    String line = rs.getString(1);
                    rawPlan.append(line).append("\n");
                    
                    String lower = line.toLowerCase();
                    
                    if (lower.contains("index") && !lower.contains("no index")) {
                        usesIndex = true;
                    }
                    
                    if (lower.contains("table scan") || lower.contains("tableScan")) {
                        fullTableScan = true;
                    }
                }
            }
        }
        
        List<String> recommendations = buildRecommendations(
            usesIndex, fullTableScan, rawPlan.toString(), sql
        );
        
        return QueryPlanResult.builder()
            .databaseType(DatabaseType.H2)
            .query(sql)
            .usesIndex(usesIndex)
            .indexName(usesIndex ? "index" : null)
            .fullTableScan(fullTableScan)
            .estimatedRows(0) // H2 doesn't provide row estimates easily
            .estimatedCost(0)
            .accessType(fullTableScan ? "TABLE SCAN" : usesIndex ? "INDEX" : "UNKNOWN")
            .recommendations(recommendations)
            .rawPlan(rawPlan.toString())
            .build();
    }
    
    private List<String> buildRecommendations(
        boolean usesIndex,
        boolean fullTableScan,
        String rawPlan,
        String sql) {
        
        List<String> recommendations = new ArrayList<>();
        
        // Table scan detected
        if (fullTableScan) {
            recommendations.add("Table scan detected");
            recommendations.add("Query filters data - consider adding index on filtered columns");
        }
        
        // No index detected
        if (!usesIndex && !fullTableScan) {
            recommendations.add("No index detected in execution plan");
        }
        
        // Good - using index
        if (usesIndex) {
            recommendations.add("Query uses index");
        }
        
        recommendations.add("Note: H2 provides limited plan details - verify on production database");
        
        return recommendations;
    }
}
