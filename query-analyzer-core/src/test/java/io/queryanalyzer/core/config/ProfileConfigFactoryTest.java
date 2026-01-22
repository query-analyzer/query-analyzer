package io.queryanalyzer.core.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ProfileConfigFactoryTest {

    @Test
    void shouldCreateBalancedConfig() {
        DetectorConfig config = ProfileConfigFactory.createConfig(DetectionProfile.BALANCED);
        
        assertThat(config.getMinConfidenceThreshold()).isEqualTo(0.5);
        assertThat(config.getMinRepetitions()).isEqualTo(3);
        assertThat(config.getMaxQueriesToAnalyze()).isEqualTo(5000); // Handles most scenarios
        assertThat(config.getStackTraceWeight()).isEqualTo(0.5);
        assertThat(config.getTimingWeight()).isEqualTo(0.2);
        assertThat(config.getPatternWeight()).isEqualTo(0.3);
        assertThat(config.getDeliberatePacingThresholdMs()).isEqualTo(50);
    }

    @Test
    void shouldCreateStrictConfig() {
        DetectorConfig config = ProfileConfigFactory.createConfig(DetectionProfile.STRICT);
        
        assertThat(config.getMinConfidenceThreshold()).isEqualTo(0.3);
        assertThat(config.getMinRepetitions()).isEqualTo(2);
        assertThat(config.getDeliberatePacingThresholdMs()).isEqualTo(100);
        assertThat(config.getWarningTimeMs()).isEqualTo(100);
    }

    @Test
    void shouldCreateLenientConfig() {
        DetectorConfig config = ProfileConfigFactory.createConfig(DetectionProfile.LENIENT);
        
        assertThat(config.getMinConfidenceThreshold()).isEqualTo(0.7);
        assertThat(config.getMinRepetitions()).isEqualTo(5);
        assertThat(config.getMaxQueriesToAnalyze()).isEqualTo(10000);
        assertThat(config.getDeliberatePacingThresholdMs()).isEqualTo(100);
        assertThat(config.getMaxCoefficientOfVariation()).isEqualTo(0.7);
    }

    @Test
    void shouldValidateAllProfiles() {
        for (DetectionProfile profile : DetectionProfile.values()) {
            DetectorConfig config = ProfileConfigFactory.createConfig(profile);
            assertThatCode(() -> config.validate()).doesNotThrowAnyException();
        }
    }

    @Test
    void shouldAllowOverrides() {
        DetectorConfig config = ProfileConfigFactory.createConfigWithOverrides(
            DetectionProfile.BALANCED,
            base -> base.toBuilder().minConfidenceThreshold(0.4).build()
        );
        
        assertThat(config.getMinConfidenceThreshold()).isEqualTo(0.4);
        assertThat(config.getMinRepetitions()).isEqualTo(3);
        assertThat(config.getStackTraceWeight()).isEqualTo(0.5);
    }

    @Test
    void shouldValidateAfterOverrides() {
        assertThatThrownBy(() ->
            ProfileConfigFactory.createConfigWithOverrides(
                DetectionProfile.BALANCED,
                base -> base.toBuilder().minConfidenceThreshold(1.5).build()
            )
        ).isInstanceOf(IllegalArgumentException.class);
    }
    
    @Test
    void shouldWeightsSumToOne() {
        for (DetectionProfile profile : DetectionProfile.values()) {
            DetectorConfig config = ProfileConfigFactory.createConfig(profile);
            double sum = config.getStackTraceWeight() + 
                        config.getTimingWeight() + 
                        config.getPatternWeight();
            assertThat(sum).isCloseTo(1.0, within(0.001));
        }
    }
    
    @Test
    void shouldHaveOrderedSeverityThresholds() {
        for (DetectionProfile profile : DetectionProfile.values()) {
            DetectorConfig config = ProfileConfigFactory.createConfig(profile);
            
            assertThat(config.getWarningTimeMs()).isLessThan(config.getErrorTimeMs());
            assertThat(config.getErrorTimeMs()).isLessThan(config.getCriticalTimeMs());
            
            assertThat(config.getWarningQueryCount()).isLessThan(config.getErrorQueryCount());
            assertThat(config.getErrorQueryCount()).isLessThan(config.getCriticalQueryCount());
        }
    }
}
