package io.queryanalyzer.core.config;

import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class DetectorConfig {
    
    public enum DetectionMode {
        /**
         * Uses stack trace, timing, and pattern analysis with weighted scoring.
         * Most accurate but may have false negatives for non-Hibernate frameworks.
         */
        CONFIDENCE,
        
        /**
         * Simple threshold-based detection
         * Faster, more predictable, works with any framework.
         */
        THRESHOLD,
        
        /**
         * Both confidence AND threshold must agree.
         * Most conservative - fewest false positives.
         */
        HYBRID
    }
    

    @Builder.Default
    DetectionMode detectionMode = DetectionMode.CONFIDENCE;
    

    @Builder.Default
    boolean enableFrameworkSuggestions = true;

    @Builder.Default
    boolean enableRelationshipInference = true;
    

    @Builder.Default
    int minRepetitions = 3;
    

    @Builder.Default
    int maxQueriesToAnalyze = 5000;
    

    @Builder.Default
    double minConfidenceThreshold = 0.5;
    

    @Builder.Default
    double stackTraceWeight = 0.5;

    @Builder.Default
    double timingWeight = 0.2;

    @Builder.Default
    double patternWeight = 0.3;
    
    @Builder.Default
    long deliberatePacingThresholdMs = 50;

    @Builder.Default
    int minSamplesForVariance = 5;
    
    @Builder.Default
    double maxCoefficientOfVariation = 0.5;

    @Builder.Default
    long tightLoopThresholdMs = 1000;
    
    @Builder.Default
    long moderateLoopThresholdMs = 3000;
    
    @Builder.Default
    long slowLoopThresholdMs = 10000;

    @Builder.Default
    long warningTimeMs = 200;
    
    @Builder.Default
    int warningQueryCount = 20;
    
    @Builder.Default
    long errorTimeMs = 500;
    
    @Builder.Default
    int errorQueryCount = 50;
    
    @Builder.Default
    long criticalTimeMs = 2000;
    
    @Builder.Default
    int criticalQueryCount = 100;
    
    /**
     * Minimum percentage of queries that must have sufficient stack depth
     * for loop pattern detection. Range: 0.5 to 1.0
     */
    @Builder.Default
    double loopDetectionMinQueriesRatio = 0.8;

    public static DetectorConfig defaults() {
        return DetectorConfig.builder().build();
    }

    public void validate() {
        if (minRepetitions < 2 || minRepetitions > 10) {
            throw new IllegalArgumentException(
                "minRepetitions must be between 2 and 10, got: " + minRepetitions);
        }
        
        if (maxQueriesToAnalyze < 100 || maxQueriesToAnalyze > 50000) {
            throw new IllegalArgumentException(
                "maxQueriesToAnalyze must be between 100 and 50000, got: " + maxQueriesToAnalyze);
        }
        
        if (minRepetitions > maxQueriesToAnalyze) {
            throw new IllegalArgumentException(
                "minRepetitions (" + minRepetitions + ") must be <= maxQueriesToAnalyze (" + 
                maxQueriesToAnalyze + ")");
        }
        
        if (minConfidenceThreshold < 0.0 || minConfidenceThreshold > 1.0) {
            throw new IllegalArgumentException(
                "minConfidenceThreshold must be between 0.0 and 1.0, got: " + minConfidenceThreshold);
        }
        
        // Validate weights
        if (stackTraceWeight < 0.0 || stackTraceWeight > 1.0) {
            throw new IllegalArgumentException(
                "stackTraceWeight must be between 0.0 and 1.0, got: " + stackTraceWeight);
        }
        
        if (timingWeight < 0.0 || timingWeight > 1.0) {
            throw new IllegalArgumentException(
                "timingWeight must be between 0.0 and 1.0, got: " + timingWeight);
        }
        
        if (patternWeight < 0.0 || patternWeight > 1.0) {
            throw new IllegalArgumentException(
                "patternWeight must be between 0.0 and 1.0, got: " + patternWeight);
        }
        
        double weightSum = stackTraceWeight + timingWeight + patternWeight;
        if (Math.abs(weightSum - 1.0) > 0.001) {
            throw new IllegalArgumentException(
                String.format("Confidence weights must sum to 1.0, got: %.3f " +
                    "(stackTrace=%.2f, timing=%.2f, pattern=%.2f)",
                    weightSum, stackTraceWeight, timingWeight, patternWeight));
        }
        
        if (deliberatePacingThresholdMs < 10 || deliberatePacingThresholdMs > 500) {
            throw new IllegalArgumentException(
                "deliberatePacingThresholdMs must be between 10 and 500, got: " + 
                deliberatePacingThresholdMs);
        }
        
        if (minSamplesForVariance < 3 || minSamplesForVariance > 20) {
            throw new IllegalArgumentException(
                "minSamplesForVariance must be between 3 and 20, got: " + minSamplesForVariance);
        }
        
        if (maxCoefficientOfVariation < 0.1 || maxCoefficientOfVariation > 1.0) {
            throw new IllegalArgumentException(
                "maxCoefficientOfVariation must be between 0.1 and 1.0, got: " + 
                maxCoefficientOfVariation);
        }
        
        if (tightLoopThresholdMs >= moderateLoopThresholdMs) {
            throw new IllegalArgumentException(
                "tightLoopThresholdMs (" + tightLoopThresholdMs + ") must be < " +
                "moderateLoopThresholdMs (" + moderateLoopThresholdMs + ")");
        }
        
        if (moderateLoopThresholdMs >= slowLoopThresholdMs) {
            throw new IllegalArgumentException(
                "moderateLoopThresholdMs (" + moderateLoopThresholdMs + ") must be < " +
                "slowLoopThresholdMs (" + slowLoopThresholdMs + ")");
        }
        
        if (warningTimeMs >= errorTimeMs) {
            throw new IllegalArgumentException(
                "warningTimeMs (" + warningTimeMs + ") must be < errorTimeMs (" + 
                errorTimeMs + ")");
        }
        
        if (errorTimeMs >= criticalTimeMs) {
            throw new IllegalArgumentException(
                "errorTimeMs (" + errorTimeMs + ") must be < criticalTimeMs (" + 
                criticalTimeMs + ")");
        }
        
        if (warningQueryCount >= errorQueryCount) {
            throw new IllegalArgumentException(
                "warningQueryCount (" + warningQueryCount + ") must be < errorQueryCount (" + 
                errorQueryCount + ")");
        }
        
        if (errorQueryCount >= criticalQueryCount) {
            throw new IllegalArgumentException(
                "errorQueryCount (" + errorQueryCount + ") must be < criticalQueryCount (" + 
                criticalQueryCount + ")");
        }
        
        if (loopDetectionMinQueriesRatio < 0.5 || loopDetectionMinQueriesRatio > 1.0) {
            throw new IllegalArgumentException(
                "loopDetectionMinQueriesRatio must be between 0.5 and 1.0, got: " + 
                loopDetectionMinQueriesRatio);
        }
    }
    

    public static class DetectorConfigBuilder {

        public DetectorConfig buildAndValidate() {
            DetectorConfig config = this.build();
            config.validate();
            return config;
        }
    }
}
