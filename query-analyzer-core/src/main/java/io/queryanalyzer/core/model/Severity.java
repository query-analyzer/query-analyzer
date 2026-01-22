package io.queryanalyzer.core.model;


public enum Severity {


    INFO,

    /**
     * Warning - Query should be optimized when possible.
     * Typically for queries between 100-500ms.
     */
    WARNING,

    /**
     * Error - Query has serious performance issues.
     * Typically for queries between 500-2000ms.
     */
    ERROR,

    /**
     * Critical - Query poses production risk.
     * Typically for queries over 2000ms.
     */
    CRITICAL;

    public static Severity fromExecutionTime(long executionTimeMs) {
        if (executionTimeMs < 100) {
            return INFO;
        } else if (executionTimeMs < 500) {
            return WARNING;
        } else if (executionTimeMs < 2000) {
            return ERROR;
        } else {
            return CRITICAL;
        }
    }
}
