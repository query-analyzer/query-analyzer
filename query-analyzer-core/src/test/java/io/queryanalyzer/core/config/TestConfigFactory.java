package io.queryanalyzer.core.config;

import io.queryanalyzer.core.detector.confidence.ConfidenceAnalyzer;
import io.queryanalyzer.core.detector.timing.TimingAnalyzer;


public class TestConfigFactory {
    

    public static DetectorConfig createDefault() {
        return ProfileConfigFactory.createConfig(DetectionProfile.BALANCED);
    }
    

    public static DetectorConfig createWithMinConfidence(double minConfidence) {
        return createDefault().toBuilder()
            .minConfidenceThreshold(minConfidence)
            .build();
    }
    

    public static DetectorConfig createWithMinRepetitions(int minRepetitions) {
        return createDefault().toBuilder()
            .minRepetitions(minRepetitions)
            .build();
    }
    

    public static DetectorConfig createWithMaxQueries(int maxQueries) {
        return createDefault().toBuilder()
            .maxQueriesToAnalyze(maxQueries)
            .build();
    }
    

    public static DetectorConfig createStrict() {
        return ProfileConfigFactory.createConfig(DetectionProfile.STRICT);
    }
    

    public static DetectorConfig createLenient() {
        return ProfileConfigFactory.createConfig(DetectionProfile.LENIENT);
    }
    

    public static TimingAnalyzer createTimingAnalyzer() {
        return new TimingAnalyzer(createDefault());
    }

    public static ConfidenceAnalyzer createConfidenceAnalyzer() {
        DetectorConfig config = createDefault();
        TimingAnalyzer timingAnalyzer = new TimingAnalyzer(config);
        return new ConfidenceAnalyzer(timingAnalyzer, config);
    }

    public static ConfidenceAnalyzer createConfidenceAnalyzer(DetectorConfig config) {
        TimingAnalyzer timingAnalyzer = new TimingAnalyzer(config);
        return new ConfidenceAnalyzer(timingAnalyzer, config);
    }
}
