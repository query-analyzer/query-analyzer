package io.queryanalyzer.core.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ConfigurationValidator {

    private static final Logger log = LoggerFactory.getLogger(ConfigurationValidator.class);


    public void validateThresholds(long info, long warning, long error, long critical) {
        log.debug("Validating thresholds: info={}, warning={}, error={}, critical={}", 
                 info, warning, error, critical);

        // Check non-negative
        if (info < 0) {
            throw ConfigurationException.negativeValue("thresholds.info", info);
        }
        if (warning < 0) {
            throw ConfigurationException.negativeValue("thresholds.warning", warning);
        }
        if (error < 0) {
            throw ConfigurationException.negativeValue("thresholds.error", error);
        }
        if (critical < 0) {
            throw ConfigurationException.negativeValue("thresholds.critical", critical);
        }

        if (info >= warning) {
            throw ConfigurationException.invalidThresholdOrder("info", info, "warning", warning);
        }
        if (warning >= error) {
            throw ConfigurationException.invalidThresholdOrder("warning", warning, "error", error);
        }
        if (error >= critical) {
            throw ConfigurationException.invalidThresholdOrder("error", error, "critical", critical);
        }

        log.info("Threshold validation passed");
    }


    public void validateConfidenceThreshold(double minConfidence) {
        log.debug("Validating confidence threshold: {}", minConfidence);

        if (minConfidence < 0.0 || minConfidence > 1.0) {
            throw ConfigurationException.invalidConfidenceRange(minConfidence);
        }

        log.debug("Confidence threshold validation passed");
    }


    public void validateDetectionConfig(boolean nPlusOneEnabled, boolean slowQueryEnabled) {
        log.debug("Validating detection config: n+1={}, slowQuery={}", 
                 nPlusOneEnabled, slowQueryEnabled);

        if (!nPlusOneEnabled && !slowQueryEnabled) {
            log.warn("Both N+1 and slow query detection are disabled. " +
                    "Query Analyzer will not detect any issues.");
        }

        log.debug("Detection config validation passed");
    }


    public void validateAll(QueryAnalyzerConfig config) {
        if (config == null) {
            throw new ConfigurationException("Configuration cannot be null");
        }

        log.info("Starting configuration validation");

        try {
            validateThresholds(
                config.getInfoThreshold(),
                config.getWarningThreshold(),
                config.getErrorThreshold(),
                config.getCriticalThreshold()
            );

            if (config.getMinConfidence() != null) {
                validateConfidenceThreshold(config.getMinConfidence());
            }

            validateDetectionConfig(
                config.isNPlusOneEnabled(),
                config.isSlowQueryEnabled()
            );

            log.info("Configuration validation completed successfully");
        } catch (ConfigurationException e) {
            log.error("Configuration validation failed: {}", e.getMessage());
            throw e;
        }
    }


    public interface QueryAnalyzerConfig {
        long getInfoThreshold();
        long getWarningThreshold();
        long getErrorThreshold();
        long getCriticalThreshold();
        Double getMinConfidence();
        boolean isNPlusOneEnabled();
        boolean isSlowQueryEnabled();
    }
}
