package io.queryanalyzer.spring.controller;

import io.queryanalyzer.core.metrics.MetricsCollector;
import io.queryanalyzer.core.metrics.PrometheusMetricsExporter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/actuator/query-analyzer")
@ConditionalOnProperty(prefix = "query-analyzer.metrics", name = "enabled", havingValue = "true")
public class MetricsController {

    private final PrometheusMetricsExporter exporter;
    private final MetricsCollector collector;

    public MetricsController(MetricsCollector collector) {
        this.collector = collector;
        this.exporter = new PrometheusMetricsExporter(collector);
    }


    @GetMapping(value = "/metrics", produces = "text/plain")
    public String getMetricsPrometheus() {
        return exporter.export();
    }


    @GetMapping(value = "/metrics", produces = MediaType.APPLICATION_JSON_VALUE)
    public MetricsCollector.MetricsSnapshot getMetricsJson() {
        return collector.getSnapshot();
    }


    @GetMapping("/health")
    public HealthStatus getHealth() {
        return new HealthStatus("UP", collector.getTotalRequestsAnalyzed());
    }

    public static class HealthStatus {
        private final String status;
        private final long requestsAnalyzed;

        public HealthStatus(String status, long requestsAnalyzed) {
            this.status = status;
            this.requestsAnalyzed = requestsAnalyzed;
        }

        public String getStatus() {
            return status;
        }

        public long getRequestsAnalyzed() {
            return requestsAnalyzed;
        }
    }
}
