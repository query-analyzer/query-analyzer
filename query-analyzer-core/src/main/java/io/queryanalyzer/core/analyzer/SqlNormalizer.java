package io.queryanalyzer.core.analyzer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


public final class SqlNormalizer {

    // Matches string literals including escaped quotes
    private static final Pattern STRING_PATTERN = Pattern.compile("'(?:''|[^'])*'");
    
    // Matches numbers that should be replaced (not after LIMIT/OFFSET/FETCH/TOP)
    // Uses negative lookbehind to preserve pagination values
    private static final Pattern REPLACEABLE_NUMBER_PATTERN = Pattern.compile(
        "(?<!LIMIT\\s)(?<!LIMIT\\s\\s)(?<!OFFSET\\s)(?<!OFFSET\\s\\s)" +
        "(?<!FETCH\\s)(?<!FETCH\\s\\s)(?<!TOP\\s)(?<!TOP\\s\\s)" +
        "(?<!_)\\b\\d+\\b(?!_)"
    );
    
    // Matches multiple whitespace
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    
    // Matches UUID patterns (preserve them for correlation)
    private static final Pattern UUID_PATTERN = Pattern.compile(
        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
    );
    
    private static final Pattern BLOCK_COMMENT_PATTERN = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT_PATTERN = Pattern.compile("--[^\r\n]*");

    private SqlNormalizer() {
    }
    
    public static String normalize(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return "";
        }

        String normalized = sql;
        
        // Strip comments FIRST (critical for PreparedStatement param comments)
        // Handle potentially malformed comments safely
        try {
            normalized = BLOCK_COMMENT_PATTERN.matcher(normalized).replaceAll("");
        } catch (Exception e) {
            // If regex fails on malformed input, continue with original
        }
        try {
            normalized = LINE_COMMENT_PATTERN.matcher(normalized).replaceAll("");
        } catch (Exception e) {
            // If regex fails on malformed input, continue with original
        }
        
        normalized = UUID_PATTERN.matcher(normalized).replaceAll("?uuid?");

        normalized = STRING_PATTERN.matcher(normalized).replaceAll("?");

        normalized = normalizeNumbers(normalized);

        normalized = WHITESPACE_PATTERN.matcher(normalized).replaceAll(" ");

        normalized = normalized.toLowerCase().trim();
        
        normalized = normalized.replace("?uuid?", "?");

        return normalized;
    }
    
    private static String normalizeNumbers(String sql) {
        String upperSql = sql.toUpperCase();
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;
        
        Matcher matcher = Pattern.compile("\\b\\d+\\b").matcher(sql);
        
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            
            result.append(sql, lastEnd, start);
            
            if (shouldPreserveNumber(upperSql, start)) {
                result.append(sql, start, end);
            } else {
                result.append("?");
            }
            
            lastEnd = end;
        }
        
        result.append(sql.substring(lastEnd));
        
        return result.toString();
    }
    

    private static boolean shouldPreserveNumber(String upperSql, int position) {
        if (position > 0 && upperSql.charAt(position - 1) == '_') {
            return true;
        }
        
        String[] paginationKeywords = {"LIMIT ", "OFFSET ", "FETCH ", "TOP ", "FIRST ", "NEXT "};
        
        int lookbackStart = Math.max(0, position - 20);
        String lookback = upperSql.substring(lookbackStart, position);
        
        for (String keyword : paginationKeywords) {
            int keywordPos = lookback.lastIndexOf(keyword);
            if (keywordPos >= 0) {
                String between = lookback.substring(keywordPos + keyword.length());
                if (between.trim().isEmpty()) {
                    return true;
                }
            }
        }
        
        return false;
    }

    public static NormalizationResult normalizeWithMetadata(String sql) {
        String normalized = normalize(sql);
        Integer limit = extractLimit(sql);
        Integer offset = extractOffset(sql);
        
        return new NormalizationResult(normalized, limit, offset);
    }
    

    public static Integer extractLimit(String sql) {
        if (sql == null) return null;
        
        Pattern limitPattern = Pattern.compile(
            "\\bLIMIT\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = limitPattern.matcher(sql);
        
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
    

    public static Integer extractOffset(String sql) {
        if (sql == null) return null;
        
        Pattern offsetPattern = Pattern.compile(
            "\\bOFFSET\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = offsetPattern.matcher(sql);
        
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    public static String extractQueryType(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return "UNKNOWN";
        }

        String trimmed = sql.trim().toUpperCase();

        if (trimmed.startsWith("SELECT")) return "SELECT";
        if (trimmed.startsWith("INSERT")) return "INSERT";
        if (trimmed.startsWith("UPDATE")) return "UPDATE";
        if (trimmed.startsWith("DELETE")) return "DELETE";
        
        if (trimmed.startsWith("CREATE")) return "CREATE";
        if (trimmed.startsWith("DROP")) return "DROP";
        if (trimmed.startsWith("ALTER")) return "ALTER";
        if (trimmed.startsWith("TRUNCATE")) return "TRUNCATE";
        
        if (trimmed.startsWith("MERGE")) return "MERGE";
        if (trimmed.startsWith("WITH")) return "WITH";
        if (trimmed.startsWith("CALL")) return "CALL";
        if (trimmed.startsWith("EXECUTE")) return "EXECUTE";
        if (trimmed.startsWith("EXEC")) return "EXEC";

        return "UNKNOWN";
    }
    

    public static class NormalizationResult {
        private final String normalizedSql;
        private final Integer limit;
        private final Integer offset;
        
        public NormalizationResult(String normalizedSql, Integer limit, Integer offset) {
            this.normalizedSql = normalizedSql;
            this.limit = limit;
            this.offset = offset;
        }
        
        public String getNormalizedSql() { return normalizedSql; }
        public Integer getLimit() { return limit; }
        public Integer getOffset() { return offset; }
        
        public boolean hasPagination() {
            return limit != null || offset != null;
        }
    }
}
