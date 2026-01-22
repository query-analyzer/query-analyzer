package io.queryanalyzer.core.metrics;

import io.queryanalyzer.core.model.IssueType;
import io.queryanalyzer.core.model.Severity;


public class PrometheusMetricsExporter {

    private final MetricsCollector collector;

    public PrometheusMetricsExporter(MetricsCollector collector) {
        this.collector = collector;
    }


    public String export() {
        StringBuilder sb = new StringBuilder();

        appendMetric(sb, "query_analyzer_issues_total",
                    "Total number of query issues detected", 
                    "counter",
                    collector.getTotalIssuesDetected());

        appendMetricWithLabel(sb, "query_analyzer_issues_by_type_total",
                             "Total issues by type",
                             "counter",
                             "type", "n_plus_one",
                             collector.getIssuesByType(IssueType.N_PLUS_ONE));
        appendMetricWithLabel(sb, "query_analyzer_issues_by_type_total",
                             null,
                             "counter",
                             "type", "slow_query",
                             collector.getIssuesByType(IssueType.SLOW_QUERY));

        appendMetricWithLabel(sb, "query_analyzer_issues_by_severity_total",
                             "Total issues by severity",
                             "counter",
                             "severity", "info",
                             collector.getIssuesBySeverity(Severity.INFO));
        appendMetricWithLabel(sb, "query_analyzer_issues_by_severity_total",
                             null,
                             "counter",
                             "severity", "warning",
                             collector.getIssuesBySeverity(Severity.WARNING));
        appendMetricWithLabel(sb, "query_analyzer_issues_by_severity_total",
                             null,
                             "counter",
                             "severity", "error",
                             collector.getIssuesBySeverity(Severity.ERROR));
        appendMetricWithLabel(sb, "query_analyzer_issues_by_severity_total",
                             null,
                             "counter",
                             "severity", "critical",
                             collector.getIssuesBySeverity(Severity.CRITICAL));

        appendMetric(sb, "query_analyzer_requests_analyzed_total",
                    "Total number of requests analyzed",
                    "counter",
                    collector.getTotalRequestsAnalyzed());

        appendMetric(sb, "query_analyzer_queries_current",
                    "Current number of queries in active request",
                    "gauge",
                    collector.getCurrentQueriesInRequest());

        appendMetric(sb, "query_analyzer_detection_duration_ms",
                    "Duration of last detection operation in milliseconds",
                    "gauge",
                    collector.getLastDetectionDurationMs());

        appendHistogram(sb, "query_analyzer_queries_per_request",
                       "Distribution of query counts per request");

        appendDetectionDurationHistogram(sb, "query_analyzer_detection_duration_seconds",
                                        "Distribution of detection operation durations");

        return sb.toString();
    }

    private void appendMetric(StringBuilder sb, String name, String help, String type, long value) {
        sb.append("# HELP ").append(name).append(" ").append(help).append("\n");
        sb.append("# TYPE ").append(name).append(" ").append(type).append("\n");
        sb.append(name).append(" ").append(value).append("\n");
        sb.append("\n");
    }

    private void appendMetricWithLabel(StringBuilder sb, String name, String help, 
                                      String type, String labelName, String labelValue, long value) {
        if (help != null) {
            sb.append("# HELP ").append(name).append(" ").append(help).append("\n");
            sb.append("# TYPE ").append(name).append(" ").append(type).append("\n");
        }
        sb.append(name).append("{").append(labelName).append("=\"").append(labelValue).append("\"} ")
          .append(value).append("\n");
    }

    private void appendHistogram(StringBuilder sb, String name, String help) {
        sb.append("# HELP ").append(name).append(" ").append(help).append("\n");
        sb.append("# TYPE ").append(name).append(" histogram\n");

        long cumulative = 0;
        cumulative += collector.getQueryCountBucket("0-10");
        sb.append(name).append("_bucket{le=\"10\"} ").append(cumulative).append("\n");

        cumulative += collector.getQueryCountBucket("11-50");
        sb.append(name).append("_bucket{le=\"50\"} ").append(cumulative).append("\n");

        cumulative += collector.getQueryCountBucket("51-100");
        sb.append(name).append("_bucket{le=\"100\"} ").append(cumulative).append("\n");

        cumulative += collector.getQueryCountBucket("101-500");
        sb.append(name).append("_bucket{le=\"500\"} ").append(cumulative).append("\n");

        cumulative += collector.getQueryCountBucket("500+");
        sb.append(name).append("_bucket{le=\"+Inf\"} ").append(cumulative).append("\n");

        sb.append(name).append("_count ").append(cumulative).append("\n");
        sb.append("\n");
    }

    private void appendDetectionDurationHistogram(StringBuilder sb, String name, String help) {
        sb.append("# HELP ").append(name).append(" ").append(help).append("\n");
        sb.append("# TYPE ").append(name).append(" histogram\n");

        long cumulative = 0;
        cumulative += collector.getDetectionDurationBucket("0-1");
        sb.append(name).append("_bucket{le=\"0.001\"} ").append(cumulative).append("\n");

        cumulative += collector.getDetectionDurationBucket("1-5");
        sb.append(name).append("_bucket{le=\"0.005\"} ").append(cumulative).append("\n");

        cumulative += collector.getDetectionDurationBucket("5-10");
        sb.append(name).append("_bucket{le=\"0.010\"} ").append(cumulative).append("\n");

        cumulative += collector.getDetectionDurationBucket("10-50");
        sb.append(name).append("_bucket{le=\"0.050\"} ").append(cumulative).append("\n");

        cumulative += collector.getDetectionDurationBucket("50+");
        sb.append(name).append("_bucket{le=\"+Inf\"} ").append(cumulative).append("\n");

        sb.append(name).append("_count ").append(cumulative).append("\n");
        sb.append("\n");
    }
}
