package io.queryanalyzer.core.analyzer;

import io.queryanalyzer.core.model.QueryInfo;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class ForeignKeyPatternAnalyzer {
    
    // Pattern: column_name = ? in WHERE clause (snake_case FK)
    private static final Pattern FK_SNAKE_CASE = Pattern.compile(
        "WHERE\\s+(?:\\w+\\.)?([a-z_]+_id)\\s*=\\s*(?:\\?|\\d+|'[^']*')",
        Pattern.CASE_INSENSITIVE
    );
    
    // Pattern: columnName = ? in WHERE clause (camelCase FK)
    private static final Pattern FK_CAMEL_CASE = Pattern.compile(
        "WHERE\\s+(?:\\w+\\.)?([a-z]+Id)\\s*=\\s*(?:\\?|\\d+|'[^']*')",
        Pattern.CASE_INSENSITIVE
    );
    
    // Pattern: fk_table = ? in WHERE clause
    private static final Pattern FK_PREFIX = Pattern.compile(
        "WHERE\\s+(?:\\w+\\.)?(fk_[a-z_]+)\\s*=\\s*(?:\\?|\\d+|'[^']*')",
        Pattern.CASE_INSENSITIVE
    );
    
    // Pattern: table_id IN (?, ?, ?) - batch pattern
    private static final Pattern FK_IN_CLAUSE = Pattern.compile(
        "WHERE\\s+(?:\\w+\\.)?([a-z_]+_id)\\s+IN\\s*\\(",
        Pattern.CASE_INSENSITIVE
    );
    
    // Pattern: FROM table_name - extract table
    private static final Pattern FROM_TABLE = Pattern.compile(
        "FROM\\s+[\"'`]?([a-z_][a-z0-9_]*)[\"'`]?",
        Pattern.CASE_INSENSITIVE
    );

    // Infer relationship from a list of repeated queries
    public Optional<InferredRelationship> inferRelationship(List<QueryInfo> queries) {
        if (queries == null || queries.isEmpty()) {
            return Optional.empty();
        }
        
        String sql = queries.get(0).getSql();
        if (sql == null || sql.trim().isEmpty()) {
            return Optional.empty();
        }
        
        return inferFromSql(sql);
    }
    
    // from sql
    public Optional<InferredRelationship> inferFromSql(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return Optional.empty();
        }
        
        String upperSql = sql.toUpperCase();
        
        // Only analyze SELECT queries (most common for N+1)
        if (!upperSql.trim().startsWith("SELECT")) {
            return Optional.empty();
        }
        
        String childTable = extractTableFromSql(sql);
        if (childTable == null || "unknown".equals(childTable)) {
            return Optional.empty();
        }
        
        return tryInferFromForeignKey(sql, childTable);
    }

    // Try to infer relationship from foreign key column patterns.
    private Optional<InferredRelationship> tryInferFromForeignKey(String sql, String childTable) {
        
        // Try snake_case pattern (e.g., user_id)
        Matcher snakeMatcher = FK_SNAKE_CASE.matcher(sql);
        if (snakeMatcher.find()) {
            String fkColumn = snakeMatcher.group(1).toLowerCase();
            return buildRelationship(childTable, fkColumn, 1.0);
        }
        
        // Try camelCase pattern (e.g., userId)
        Matcher camelMatcher = FK_CAMEL_CASE.matcher(sql);
        if (camelMatcher.find()) {
            String fkColumn = camelMatcher.group(1);
            return buildRelationship(childTable, fkColumn, 0.9);
        }
        
        // Try fk_ prefix pattern (e.g., fk_user)
        Matcher prefixMatcher = FK_PREFIX.matcher(sql);
        if (prefixMatcher.find()) {
            String fkColumn = prefixMatcher.group(1).toLowerCase();
            return buildRelationship(childTable, fkColumn, 0.8);
        }
        
        // Try IN clause pattern (batch query)
        Matcher inMatcher = FK_IN_CLAUSE.matcher(sql);
        if (inMatcher.find()) {
            String fkColumn = inMatcher.group(1).toLowerCase();
            return buildRelationship(childTable, fkColumn, 0.9);
        }
        
        return Optional.empty();
    }

    // Build relationship inference from FK column name.
    private Optional<InferredRelationship> buildRelationship(
            String childTable, 
            String fkColumn, 
            double baseConfidence) {
        
        String parentTable = inferParentTableFromFkColumn(fkColumn);
        if (parentTable == null) {
            return Optional.empty();
        }
        
        // Adjust confidence based on naming match quality
        double confidence = adjustConfidence(baseConfidence, fkColumn, parentTable);
        
        return Optional.of(InferredRelationship.builder()
            .childTable(childTable)
            .parentTable(parentTable)
            .foreignKeyColumn(fkColumn)
            .relationshipType(InferredRelationship.RelationshipType.MANY_TO_ONE)
            .confidence(confidence)
            .build());
    }

    private String inferParentTableFromFkColumn(String fkColumn) {
        if (fkColumn == null || fkColumn.isEmpty()) {
            return null;
        }
        
        String normalized = fkColumn.toLowerCase();
        
        // Handle fk_ prefix
        if (normalized.startsWith("fk_")) {
            return normalized.substring(3);
        }
        
        // Handle _id suffix (snake_case)
        if (normalized.endsWith("_id")) {
            String base = normalized.substring(0, normalized.length() - 3);
            return pluralize(base);
        }
        
        // Handle Id suffix (camelCase) - convert to lowercase first
        if (fkColumn.endsWith("Id")) {
            String base = fkColumn.substring(0, fkColumn.length() - 2).toLowerCase();
            return base;
        }
        
        return null;
    }
    

    private String pluralize(String singular) {
        if (singular == null || singular.isEmpty()) {
            return singular;
        }
        
        if (singular.endsWith("y") && singular.length() > 1) {
            char beforeY = singular.charAt(singular.length() - 2);
            if (!isVowel(beforeY)) {
                return singular.substring(0, singular.length() - 1) + "ies";
            }
        }
        
        if (singular.endsWith("s") || singular.endsWith("x") ||
            singular.endsWith("z") || singular.endsWith("ch") || 
            singular.endsWith("sh")) {
            return singular + "es";
        }

        return singular + "s";
    }
    
    private boolean isVowel(char c) {
        return "aeiouAEIOU".indexOf(c) >= 0;
    }
    

    private double adjustConfidence(double base, String fkColumn, String parentTable) {
        // Generic names -> lower confidence (check FIRST before naming convention match)
        // These are too ambiguous to confidently infer the parent table
        if (fkColumn.equals("id") || fkColumn.equals("fk_id") || fkColumn.equals("parent_id")) {
            return Math.max(0.3, base - 0.3);
        }
        
        // Standard naming convention (table_id matches tables) → high confidence
        String expectedFk = parentTable.replaceAll("s$", "") + "_id";
        if (fkColumn.equals(expectedFk)) {
            return Math.min(1.0, base + 0.1);
        }
        
        return base;
    }
    

    private String extractTableFromSql(String sql) {
        Matcher matcher = FROM_TABLE.matcher(sql);
        if (matcher.find()) {
            return matcher.group(1).toLowerCase();
        }
        return "unknown";
    }
}
