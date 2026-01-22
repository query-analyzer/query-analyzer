package io.queryanalyzer.core.plan;

import io.queryanalyzer.core.plan.model.DatabaseType;
import io.queryanalyzer.core.plan.model.QueryPlanResult;

import java.sql.Connection;


public interface QueryPlanAnalyzer {
    

    DatabaseType getDatabaseType();
    QueryPlanResult analyze(Connection connection, String sql);
}
