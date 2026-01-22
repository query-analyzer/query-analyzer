package io.queryanalyzer.spring.autoconfigure;

import io.queryanalyzer.core.config.DetectorConfig;
import io.queryanalyzer.core.config.ProfileConfigFactory;
import io.queryanalyzer.core.config.QueryAnalyzerConfig;
import io.queryanalyzer.core.detector.NPlusOneDetector;
import io.queryanalyzer.core.detector.SlowQueryDetector;
import io.queryanalyzer.core.metrics.MetricsCollector;
import io.queryanalyzer.core.model.Severity;
import io.queryanalyzer.core.plan.QueryPlanAnalyzerFactory;
import io.queryanalyzer.core.reporter.ConsoleReporter;
import io.queryanalyzer.core.reporter.ReporterConfig;
import io.queryanalyzer.core.storage.IssueStorage;
import io.queryanalyzer.core.storage.InMemoryIssueStorage;
import io.queryanalyzer.spring.config.QueryAnalyzerProperties;
import io.queryanalyzer.spring.controller.MetricsController;
import io.queryanalyzer.spring.filter.QueryAnalysisFilter;
import io.queryanalyzer.spring.interceptor.DataSourceProxy;
import io.queryanalyzer.spring.service.QueryAnalysisOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

import javax.sql.DataSource;
import java.util.List;
import io.queryanalyzer.core.detector.QueryDetector;
import io.queryanalyzer.core.reporter.QueryReporter;

/**
 * Auto-configuration for Query Analyzer.
 */
@AutoConfiguration(after = DataSourceAutoConfiguration.class)
@ConditionalOnClass({DataSource.class})
@ConditionalOnProperty(
    prefix = "query-analyzer",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
@EnableConfigurationProperties(QueryAnalyzerProperties.class)
public class QueryAnalyzerAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(QueryAnalyzerAutoConfiguration.class);

    public QueryAnalyzerAutoConfiguration(QueryAnalyzerProperties properties) {
        log.info("");
        log.info("  Query Analyzer");
        log.info("  --------------");
        log.info("  Profile: {}", properties.getProfile());
        log.info("  Status:  ACTIVE");
        log.info("");
        
        validateConfiguration(properties);
    }

    private void validateConfiguration(QueryAnalyzerProperties properties) {
        QueryAnalyzerProperties.Thresholds t = properties.getThresholds();
        QueryAnalyzerProperties.Detection d = properties.getDetection();
        
        if (t.getInfo() >= t.getWarning()) {
            throw new IllegalArgumentException(
                "threshold.info (" + t.getInfo() + ") must be less than threshold.warning (" + t.getWarning() + ")");
        }
        if (t.getWarning() >= t.getError()) {
            throw new IllegalArgumentException(
                "threshold.warning (" + t.getWarning() + ") must be less than threshold.error (" + t.getError() + ")");
        }
        if (t.getError() >= t.getCritical()) {
            throw new IllegalArgumentException(
                "threshold.error (" + t.getError() + ") must be less than threshold.critical (" + t.getCritical() + ")");
        }
        
        // Validate confidence range if specified
        if (d.getMinConfidence() != null) {
            if (d.getMinConfidence() < 0.0 || d.getMinConfidence() > 1.0) {
                throw new IllegalArgumentException(
                    "detection.min-confidence must be between 0.0 and 1.0, got: " + d.getMinConfidence());
            }
        }
        
        log.info("Configuration validation passed");
    }
    

    @Bean
    public DetectorConfig detectorConfig(QueryAnalyzerProperties properties) {
        DetectorConfig.DetectorConfigBuilder builder =
            ProfileConfigFactory.createConfig(properties.getProfile()).toBuilder();
        
        QueryAnalyzerProperties.Detection detection = properties.getDetection();
        QueryAnalyzerProperties.AdvancedConfig advanced = detection.getAdvanced();
        
        // Detection mode override
        if (detection.getMode() != null && !detection.getMode().isBlank()) {
            try {
                DetectorConfig.DetectionMode mode = DetectorConfig.DetectionMode.valueOf(
                    detection.getMode().toUpperCase());
                builder.detectionMode(mode);
                log.debug("Override: detection-mode = {}", mode);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid detection mode '{}', using profile default. Valid values: THRESHOLD, CONFIDENCE, HYBRID", 
                    detection.getMode());
            }
        }
        
        if (detection.getMinConfidence() != null) {
            builder.minConfidenceThreshold(detection.getMinConfidence());
            log.debug("Override: min-confidence = {}", detection.getMinConfidence());
        }
        
        if (advanced != null) {
            if (advanced.getMinRepetitions() != null) {
                builder.minRepetitions(advanced.getMinRepetitions());
                log.debug("Override: min-repetitions = {}", advanced.getMinRepetitions());
            }
            
            if (advanced.getMaxQueries() != null) {
                builder.maxQueriesToAnalyze(advanced.getMaxQueries());
                log.debug("Override: max-queries = {}", advanced.getMaxQueries());
            }
            
            // Confidence weights
            if (advanced.getWeights() != null) {
                QueryAnalyzerProperties.ConfidenceWeights weights = advanced.getWeights();
                if (weights.getStackTrace() != null) {
                    builder.stackTraceWeight(weights.getStackTrace());
                }
                if (weights.getTiming() != null) {
                    builder.timingWeight(weights.getTiming());
                }
                if (weights.getPattern() != null) {
                    builder.patternWeight(weights.getPattern());
                }
                log.debug("Override: confidence weights");
            }
            
            if (advanced.getTiming() != null) {
                QueryAnalyzerProperties.TimingConfig timing = advanced.getTiming();
                if (timing.getDeliberatePacingMs() != null) {
                    builder.deliberatePacingThresholdMs(timing.getDeliberatePacingMs());
                }
                if (timing.getMinSamplesForVariance() != null) {
                    builder.minSamplesForVariance(timing.getMinSamplesForVariance());
                }
                if (timing.getMaxCoefficientOfVariation() != null) {
                    builder.maxCoefficientOfVariation(timing.getMaxCoefficientOfVariation());
                }
                if (timing.getTightLoopMs() != null) {
                    builder.tightLoopThresholdMs(timing.getTightLoopMs());
                }
                if (timing.getModerateLoopMs() != null) {
                    builder.moderateLoopThresholdMs(timing.getModerateLoopMs());
                }
                if (timing.getSlowLoopMs() != null) {
                    builder.slowLoopThresholdMs(timing.getSlowLoopMs());
                }
                log.debug("Override: timing thresholds");
            }
            
            if (advanced.getSeverity() != null) {
                QueryAnalyzerProperties.SeverityThresholds severity = advanced.getSeverity();
                if (severity.getWarningTimeMs() != null) {
                    builder.warningTimeMs(severity.getWarningTimeMs());
                }
                if (severity.getWarningQueryCount() != null) {
                    builder.warningQueryCount(severity.getWarningQueryCount());
                }
                if (severity.getErrorTimeMs() != null) {
                    builder.errorTimeMs(severity.getErrorTimeMs());
                }
                if (severity.getErrorQueryCount() != null) {
                    builder.errorQueryCount(severity.getErrorQueryCount());
                }
                if (severity.getCriticalTimeMs() != null) {
                    builder.criticalTimeMs(severity.getCriticalTimeMs());
                }
                if (severity.getCriticalQueryCount() != null) {
                    builder.criticalQueryCount(severity.getCriticalQueryCount());
                }
                log.debug("Override: severity thresholds");
            }
        }
        
        DetectorConfig config = builder.build();
        
        config.validate();
        
        log.info("DetectorConfig created from profile: {} (minConfidence={}, maxQueries={})",
            properties.getProfile(), 
            config.getMinConfidenceThreshold(), 
            config.getMaxQueriesToAnalyze());
        
        return config;
    }

    @Bean
    public QueryAnalyzerConfig queryAnalyzerConfig(QueryAnalyzerProperties properties) {
        QueryAnalyzerProperties.Thresholds t = properties.getThresholds();

        QueryAnalyzerConfig config = new QueryAnalyzerConfig(
            (int) t.getInfo(),
            (int) t.getWarning(),
            (int) t.getError(),
            (int) t.getCritical()
        );

        log.debug("Query Analyzer thresholds: INFO={}ms, WARNING={}ms, ERROR={}ms, CRITICAL={}ms",
            t.getInfo(), t.getWarning(), t.getError(), t.getCritical());

        return config;
    }

    @Bean
    @ConditionalOnProperty(
        prefix = "query-analyzer.detection",
        name = "n-plus-one",
        havingValue = "true",
        matchIfMissing = true
    )
    public NPlusOneDetector nPlusOneDetector(DetectorConfig detectorConfig) {
        log.info("N+1 query detector enabled (confidence threshold: {})",
            detectorConfig.getMinConfidenceThreshold());
        return new NPlusOneDetector(detectorConfig);
    }

    @Bean
    @ConditionalOnProperty(
        prefix = "query-analyzer.detection",
        name = "slow-queries",
        havingValue = "true",
        matchIfMissing = true
    )
    public SlowQueryDetector slowQueryDetector(QueryAnalyzerConfig config) {
        log.debug("Slow query detector enabled");
        return new SlowQueryDetector(config);
    }

    @Bean
    public ConsoleReporter consoleReporter(QueryAnalyzerProperties properties) {
        ReporterConfig config = new ReporterConfig();

        QueryAnalyzerProperties.Reporter r = properties.getReporter();
        config.setColorEnabled(r.isColors());
        config.setPrintSuggestions(r.isSuggestions());
        config.setPrintMetrics(r.isMetrics());

        try {
            Severity minSeverity = Severity.valueOf(r.getMinimumSeverity().toUpperCase());
            config.setMinimumSeverity(minSeverity);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid minimum severity '{}', using INFO", r.getMinimumSeverity());
            config.setMinimumSeverity(Severity.INFO);
        }

        log.debug("Console reporter configured: colors={}, suggestions={}, metrics={}, minSeverity={}",
            r.isColors(), r.isSuggestions(), r.isMetrics(), r.getMinimumSeverity());

        return new ConsoleReporter(config);
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public IssueStorage issueStorage() {
        log.debug("Creating InMemoryIssueStorage");
        return new InMemoryIssueStorage(1000);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public QueryPlanAnalyzerFactory queryPlanAnalyzerFactory() {
        log.debug("Creating QueryPlanAnalyzerFactory");
        return new QueryPlanAnalyzerFactory();
    }

    @Bean
    public QueryAnalysisOrchestrator queryAnalysisOrchestrator(
        @Autowired(required = false) List<QueryDetector> detectors,
        @Autowired(required = false) List<QueryReporter> reporters,
        QueryAnalyzerProperties properties,
        @Autowired(required = false) MetricsCollector metricsCollector,
        @Autowired(required = false) IssueStorage storage,
        @Autowired(required = false) DataSource dataSource,
        @Autowired(required = false) QueryPlanAnalyzerFactory planAnalyzerFactory) {

        log.debug("Creating Query Analysis Orchestrator with {} detectors, {} reporters, MetricsCollector: {}, Storage: {}, DataSource: {}, PlanAnalyzer: {}", 
            detectors != null ? detectors.size() : 0,
            reporters != null ? reporters.size() : 0,
            metricsCollector != null,
            storage != null,
            dataSource != null,
            planAnalyzerFactory != null);

        return new QueryAnalysisOrchestrator(
            detectors,
            reporters,
            properties,
            metricsCollector,
            storage,
            dataSource,
            planAnalyzerFactory
        );
    }

    @Bean
    @ConditionalOnWebApplication
    public FilterRegistrationBean<QueryAnalysisFilter> queryAnalysisFilter(
        QueryAnalysisOrchestrator orchestrator) {

        FilterRegistrationBean<QueryAnalysisFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new QueryAnalysisFilter(orchestrator));
        registration.addUrlPatterns("/*");
        registration.setName("queryAnalysisFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);

        log.info("Query Analysis Filter registered for all URL patterns");

        return registration;
    }

    @Bean
    @ConditionalOnProperty(prefix = "query-analyzer.metrics", name = "enabled", havingValue = "true")
    public MetricsCollector metricsCollector() {
        log.info("Metrics collection enabled");
        return new MetricsCollector();
    }

    @Bean
    @ConditionalOnProperty(prefix = "query-analyzer.metrics", name = "enabled", havingValue = "true")
    public MetricsController metricsController(MetricsCollector collector) {
        log.info("Metrics endpoint enabled at /actuator/query-analyzer/metrics");
        return new MetricsController(collector);
    }


    @Bean
    public static BeanPostProcessor dataSourceProxyBeanPostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof DataSource && !(bean instanceof DataSourceProxy.QueryAnalyzerMarker)) {
                    LoggerFactory.getLogger(QueryAnalyzerAutoConfiguration.class)
                        .info("Wrapping DataSource bean '{}' with Query Analyzer proxy", beanName);
                    return DataSourceProxy.wrap((DataSource) bean);
                }
                return bean;
            }
        };
    }
}
