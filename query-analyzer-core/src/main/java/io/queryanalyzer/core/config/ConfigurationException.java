package io.queryanalyzer.core.config;


public class ConfigurationException extends RuntimeException {

    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }


    public static ConfigurationException invalidThresholdOrder(String threshold1, long value1, 
                                                               String threshold2, long value2) {
        return new ConfigurationException(
            String.format("Invalid threshold ordering: %s (%d) must be less than %s (%d)", 
                         threshold1, value1, threshold2, value2)
        );
    }


    public static ConfigurationException negativeValue(String property, long value) {
        return new ConfigurationException(
            String.format("Property '%s' must be non-negative, got: %d", property, value)
        );
    }


    public static ConfigurationException invalidConfidenceRange(double value) {
        return new ConfigurationException(
            String.format("Confidence threshold must be between 0.0 and 1.0, got: %.2f", value)
        );
    }


    public static ConfigurationException missingRequired(String property) {
        return new ConfigurationException(
            String.format("Required property '%s' is missing or null", property)
        );
    }
}
