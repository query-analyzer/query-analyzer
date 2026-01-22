package io.queryanalyzer.core.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ProfileConfigFactory {
    
    private static final Logger log = LoggerFactory.getLogger(ProfileConfigFactory.class);
    
    public static DetectorConfig createConfig(DetectionProfile profile) {
        log.info("Creating detector config from profile: {}", profile);
        
        DetectorConfig config = switch (profile) {
            case BALANCED -> createBalancedConfig();
            case STRICT -> createStrictConfig();
            case LENIENT -> createLenientConfig();
        };
        
        config.validate();
        
        log.debug("Created config: minConfidence={}, maxQueries={}, weights=[{}/{}/{}]",
            config.getMinConfidenceThreshold(),
            config.getMaxQueriesToAnalyze(),
            config.getStackTraceWeight(),
            config.getTimingWeight(),
            config.getPatternWeight());
        
        return config;
    }
    

    public static DetectorConfig createConfigWithOverrides(
            DetectionProfile profile,
            java.util.function.Function<DetectorConfig, DetectorConfig> customizer) {
        
        DetectorConfig baseConfig = createConfig(profile);
        DetectorConfig customizedConfig = customizer.apply(baseConfig);
        
        customizedConfig.validate();
        
        log.info("Created config from profile {} with custom overrides", profile);
        
        return customizedConfig;
    }
    
    private static DetectorConfig createBalancedConfig() {
        return DetectorConfig.builder()
            .minRepetitions(3)
            .maxQueriesToAnalyze(5000)
            .minConfidenceThreshold(0.5)
            .stackTraceWeight(0.5)
            .timingWeight(0.2)
            .patternWeight(0.3)
            .deliberatePacingThresholdMs(50)
            .minSamplesForVariance(5)
            .maxCoefficientOfVariation(0.5)
            .tightLoopThresholdMs(1000)
            .moderateLoopThresholdMs(3000)
            .slowLoopThresholdMs(10000)
            .warningTimeMs(200)
            .warningQueryCount(20)
            .errorTimeMs(500)
            .errorQueryCount(50)
            .criticalTimeMs(2000)
            .criticalQueryCount(100)
            .build();
    }
    

    private static DetectorConfig createStrictConfig() {
        return DetectorConfig.builder()
            .minRepetitions(2)
            .maxQueriesToAnalyze(1000)
            .minConfidenceThreshold(0.3)
            .stackTraceWeight(0.5)
            .timingWeight(0.2)
            .patternWeight(0.3)
            .deliberatePacingThresholdMs(100)
            .minSamplesForVariance(5)
            .maxCoefficientOfVariation(0.6)
            .tightLoopThresholdMs(1000)
            .moderateLoopThresholdMs(3000)
            .slowLoopThresholdMs(10000)
            .warningTimeMs(100)
            .warningQueryCount(10)
            .errorTimeMs(300)
            .errorQueryCount(30)
            .criticalTimeMs(1000)
            .criticalQueryCount(50)
            .build();
    }
    
    private static DetectorConfig createLenientConfig() {
        return DetectorConfig.builder()
            .minRepetitions(5)
            .maxQueriesToAnalyze(10000)
            .minConfidenceThreshold(0.7)
            .stackTraceWeight(0.5)
            .timingWeight(0.2)
            .patternWeight(0.3)
            .deliberatePacingThresholdMs(100)
            .minSamplesForVariance(5)
            .maxCoefficientOfVariation(0.7)
            .tightLoopThresholdMs(2000)
            .moderateLoopThresholdMs(5000)
            .slowLoopThresholdMs(20000)
            .warningTimeMs(500)
            .warningQueryCount(50)
            .errorTimeMs(2000)
            .errorQueryCount(200)
            .criticalTimeMs(10000)
            .criticalQueryCount(500)
            .build();
    }

}
