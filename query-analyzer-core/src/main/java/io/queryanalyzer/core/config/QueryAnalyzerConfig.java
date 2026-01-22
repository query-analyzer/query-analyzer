package io.queryanalyzer.core.config;

import io.queryanalyzer.core.model.Severity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueryAnalyzerConfig {

    private long infoThresholdMs = 50;
    private long warningThresholdMs = 200;
    private long errorThresholdMs = 500;
    private long criticalThresholdMs = 2000;


    public Severity determineSeverity(long executionTimeMs) {
        if (executionTimeMs >= criticalThresholdMs) {
            return Severity.CRITICAL;
        } else if (executionTimeMs >= errorThresholdMs) {
            return Severity.ERROR;
        } else if (executionTimeMs >= warningThresholdMs) {
            return Severity.WARNING;
        } else {
            return Severity.INFO;
        }
    }
}
