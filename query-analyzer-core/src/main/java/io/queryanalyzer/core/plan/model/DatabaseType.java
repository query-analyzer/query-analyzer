package io.queryanalyzer.core.plan.model;


public enum DatabaseType {
    
    /**
     * MySQL and MariaDB.
     * Uses: EXPLAIN [query]
     */
    MYSQL("MySQL"),
    
    /**
     * PostgreSQL.
     * Uses: EXPLAIN (FORMAT JSON) [query]
     */
    POSTGRESQL("PostgreSQL"),
    
    /**
     * H2 Database.
     * Uses: EXPLAIN [query]
     */
    H2("H2"),
    
    /**
     * Oracle Database.
     * Not yet implemented.
     */
    ORACLE("Oracle"),
    
    /**
     * Microsoft SQL Server.
     * Not yet implemented.
     */
    SQL_SERVER("SQL Server"),
    

    UNKNOWN("Unknown");
    
    private final String displayName;
    
    DatabaseType(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public static DatabaseType fromProductName(String productName) {
        if (productName == null) {
            return UNKNOWN;
        }
        
        String lower = productName.toLowerCase();
        
        if (lower.contains("mysql") || lower.contains("mariadb")) {
            return MYSQL;
        } else if (lower.contains("postgresql") || lower.contains("postgres")) {
            return POSTGRESQL;
        } else if (lower.contains("h2")) {
            return H2;
        } else if (lower.contains("oracle")) {
            return ORACLE;
        } else if (lower.contains("sql server") || lower.contains("sqlserver")) {
            return SQL_SERVER;
        }
        
        return UNKNOWN;
    }
}
