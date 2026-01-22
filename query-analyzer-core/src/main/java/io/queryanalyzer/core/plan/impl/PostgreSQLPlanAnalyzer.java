package io.queryanalyzer.core.plan.impl;

import io.queryanalyzer.core.plan.QueryPlanAnalyzer;
import io.queryanalyzer.core.plan.model.DatabaseType;
import io.queryanalyzer.core.plan.model.QueryPlanResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;


public class PostgreSQLPlanAnalyzer implements QueryPlanAnalyzer {
    
    private static final Logger log = LoggerFactory.getLogger(PostgreSQLPlanAnalyzer.class);
    
    private static final Pattern DANGEROUS_FUNCTIONS = Pattern.compile(
        "(?i)(pg_sleep|pg_advisory_lock|pg_terminate_backend|pg_cancel_backend|" +
        "lo_import|lo_export|pg_read_file|pg_write_file|pg_ls_dir)"
    );
    
    private static final Pattern MULTIPLE_STATEMENTS = Pattern.compile(";\\s*\\w");
    
    private static final int MAX_SQL_LENGTH = 10000;
    private static final int EXPLAIN_TIMEOUT_SECONDS = 2;
    
    @Override
    public DatabaseType getDatabaseType() {
        return DatabaseType.POSTGRESQL;
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
            log.debug("Failed to execute EXPLAIN for PostgreSQL query: {}", e.getMessage());
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
        if (!sql.contains("?") && !sql.contains("$")) {
            return sql;
        }
        
        String prepared = sql.replaceAll("\\?", "1");
        
        prepared = prepared.replaceAll("\\$\\d+", "1");
        
        log.trace("Prepared parameterized SQL for EXPLAIN");
        return prepared;
    }
    
    private QueryPlanResult executeExplain(Connection connection, String sql) throws SQLException {

        String explainSql = "EXPLAIN " + sql;
        
        StringBuilder rawPlan = new StringBuilder();
        boolean usesIndex = false;
        boolean fullTableScan = false;
        long estimatedRows = 0;
        double estimatedCost = 0;
        
        try (Statement stmt = connection.createStatement()) {
            stmt.setQueryTimeout(EXPLAIN_TIMEOUT_SECONDS);
            
            try (ResultSet rs = stmt.executeQuery(explainSql)) {
            
                while (rs.next()) {
                    String line = rs.getString(1);
                    rawPlan.append(line).append("\n");
                    
                    String lower = line.toLowerCase();
                    
                    if (lower.contains("index scan") || lower.contains("index only scan")) {
                        usesIndex = true;
                    }
                    
                    if (lower.contains("seq scan")) {
                        fullTableScan = true;
                    }
                    
                    if (lower.contains("rows=")) {
                        estimatedRows = extractNumber(line, "rows=");
                    }
                    
                    if (lower.contains("cost=")) {
                        estimatedCost = extractCost(line);
                    }
                }
            }
        }
        
        List<String> recommendations = buildRecommendations(
            usesIndex, fullTableScan, estimatedRows, rawPlan.toString(), sql
        );
        
        return QueryPlanResult.builder()
            .databaseType(DatabaseType.POSTGRESQL)
            .query(sql)
            .usesIndex(usesIndex)
            .indexName(usesIndex ? "index" : null) // PostgreSQL doesn't show index name easily
            .fullTableScan(fullTableScan)
            .estimatedRows(estimatedRows)
            .estimatedCost(estimatedCost)
            .accessType(fullTableScan ? "Seq Scan" : usesIndex ? "Index Scan" : "Unknown")
            .recommendations(recommendations)
            .rawPlan(rawPlan.toString())
            .build();
    }
    
    private List<String> buildRecommendations(
        boolean usesIndex,
        boolean fullTableScan,
        long estimatedRows,
        String rawPlan,
        String sql) {
        
        List<String> recommendations = new ArrayList<>();
        
        if (fullTableScan) {
            recommendations.add("Sequential scan detected");
            
            if (estimatedRows > 10000) {
                recommendations.add(String.format(
                    "Scanning %,d rows - performance may be impacted", estimatedRows));
            } else if (estimatedRows > 1000) {
                recommendations.add(String.format(
                    "Scanning %,d rows", estimatedRows));
            }
            
            recommendations.add("Query filters data - consider adding index on filtered columns");
        }
        
        // No index used on large dataset
        if (!usesIndex && estimatedRows > 1000) {
            recommendations.add(String.format(
                "No index used - %,d rows examined", estimatedRows));
        }
        
        // Good - using index
        if (usesIndex && !fullTableScan) {
            if (rawPlan.toLowerCase().contains("index only scan")) {
                recommendations.add("Optimal: Index-only scan (no table access needed)");
            } else if (rawPlan.toLowerCase().contains("index scan")) {
                recommendations.add("Good: Using index scan");
            }
        }
        
        // Sort operations
        if (rawPlan.toLowerCase().contains("sort")) {
            recommendations.add("Sort operation required - ORDER BY without supporting index");
        }
        
        // Hash operations (GROUP BY)
        if (rawPlan.toLowerCase().contains("hash aggregate")) {
            recommendations.add("Hash aggregate for GROUP BY - consider index on grouping columns");
        }
        
        // Nested loops (could indicate join issues)
        if (rawPlan.toLowerCase().contains("nested loop")) {
            recommendations.add("Nested loop join detected");
        }
        
        return recommendations;
    }
    

    private long extractNumber(String text, String prefix) {
        try {
            int startIdx = text.indexOf(prefix);
            if (startIdx == -1) {
                return 0;
            }
            
            startIdx += prefix.length();
            int endIdx = startIdx;
            
            while (endIdx < text.length() && 
                   (Character.isDigit(text.charAt(endIdx)) || text.charAt(endIdx) == '.')) {
                endIdx++;
            }
            
            String numberStr = text.substring(startIdx, endIdx);
            return (long) Double.parseDouble(numberStr);
        } catch (Exception e) {
            return 0;
        }
    }
    
    private double extractCost(String text) {
        try {
            int startIdx = text.indexOf("cost=");
            if (startIdx == -1) {
                return 0;
            }
            
            startIdx += 5;
            int dotDotIdx = text.indexOf("..", startIdx);
            
            if (dotDotIdx != -1) {
                int endIdx = dotDotIdx + 2;
                while (endIdx < text.length() && 
                       (Character.isDigit(text.charAt(endIdx)) || text.charAt(endIdx) == '.')) {
                    endIdx++;
                }
                
                String costStr = text.substring(dotDotIdx + 2, endIdx);
                return Double.parseDouble(costStr);
            }
            
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
