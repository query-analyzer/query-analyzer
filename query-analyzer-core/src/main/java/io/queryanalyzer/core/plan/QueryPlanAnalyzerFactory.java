package io.queryanalyzer.core.plan;

import io.queryanalyzer.core.plan.impl.H2PlanAnalyzer;
import io.queryanalyzer.core.plan.impl.MySQLPlanAnalyzer;
import io.queryanalyzer.core.plan.impl.PostgreSQLPlanAnalyzer;
import io.queryanalyzer.core.plan.model.DatabaseType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;


public class QueryPlanAnalyzerFactory {
    
    private static final Logger log = LoggerFactory.getLogger(QueryPlanAnalyzerFactory.class);
    
    private final Map<DatabaseType, QueryPlanAnalyzer> analyzers;
    

    public QueryPlanAnalyzerFactory() {
        this.analyzers = new HashMap<>();
        
        registerAnalyzer(new MySQLPlanAnalyzer());
        registerAnalyzer(new PostgreSQLPlanAnalyzer());
        registerAnalyzer(new H2PlanAnalyzer());
        
        log.debug("QueryPlanAnalyzerFactory initialized with {} analyzers", 
            analyzers.size());
    }
    

    public void registerAnalyzer(QueryPlanAnalyzer analyzer) {
        if (analyzer != null) {
            analyzers.put(analyzer.getDatabaseType(), analyzer);
        }
    }
    

    public Optional<QueryPlanAnalyzer> getAnalyzer(DatabaseType type) {
        return Optional.ofNullable(analyzers.get(type));
    }
    

    public Optional<QueryPlanAnalyzer> getAnalyzer(DataSource dataSource) {
        if (dataSource == null) {
            return Optional.empty();
        }
        
        try (Connection conn = dataSource.getConnection()) {
            return getAnalyzer(conn);
        } catch (SQLException e) {
            log.debug("Failed to get connection from datasource", e);
            return Optional.empty();
        }
    }
    

    public Optional<QueryPlanAnalyzer> getAnalyzer(Connection connection) {
        if (connection == null) {
            return Optional.empty();
        }
        
        try {
            DatabaseType type = detectDatabaseType(connection);
            
            if (type == DatabaseType.UNKNOWN) {
                log.debug("Unsupported database type");
                return Optional.empty();
            }
            
            return getAnalyzer(type);
            
        } catch (SQLException e) {
            log.debug("Failed to detect database type", e);
            return Optional.empty();
        }
    }
    

    public DatabaseType detectDatabaseType(Connection connection) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        String productName = metaData.getDatabaseProductName();
        
        DatabaseType type = DatabaseType.fromProductName(productName);
        
        log.debug("Detected database: {} -> {}", productName, type);
        
        return type;
    }
    

    public boolean isSupported(DatabaseType type) {
        return analyzers.containsKey(type);
    }
    

    public java.util.Set<DatabaseType> getSupportedTypes() {
        return analyzers.keySet();
    }
}
