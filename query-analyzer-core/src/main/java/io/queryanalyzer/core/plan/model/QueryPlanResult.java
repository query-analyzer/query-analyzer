package io.queryanalyzer.core.plan.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;


@Value
@Builder
public class QueryPlanResult {

    DatabaseType databaseType;
    String query;
    boolean usesIndex;
    String indexName;
    boolean fullTableScan;
    long estimatedRows;
    double estimatedCost;
    String accessType;
    @Builder.Default
    List<String> recommendations = List.of();
    String rawPlan;
    public boolean hasPerformanceIssues() {
        return fullTableScan || (!usesIndex && estimatedRows > 1000);
    }
    

    public int getSeverityScore() {
        int score = 0;
        
        if (fullTableScan) {
            score += 5;
        }
        
        if (!usesIndex) {
            score += 3;
        }
        
        if (estimatedRows > 10000) {
            score += 2;
        } else if (estimatedRows > 1000) {
            score += 1;
        }
        
        return Math.min(score, 10);
    }
    

    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append(databaseType.getDisplayName()).append(" plan: ");
        
        if (fullTableScan) {
            sb.append("FULL TABLE SCAN");
        } else if (usesIndex) {
            sb.append("Uses index: ").append(indexName);
        } else {
            sb.append("No index");
        }
        
        sb.append(" (").append(estimatedRows).append(" rows)");
        
        if (!recommendations.isEmpty()) {
            sb.append(" - ").append(recommendations.size()).append(" recommendations");
        }
        
        return sb.toString();
    }
}
