package io.queryanalyzer.core.model;

import lombok.Builder;
import lombok.Value;


@Value
@Builder
public class ConfidenceScore {
    double overallScore;
    double stackTraceScore;
    double timingScore;
    double patternScore;
    String reasoning;


    public boolean isHighConfidence() {
        return overallScore >= 0.9;
    }


    public boolean isMediumConfidence() {
        return overallScore >= 0.7 && overallScore < 0.9;
    }


    public boolean isLowConfidence() {
        return overallScore >= 0.5 && overallScore < 0.7;
    }


    public String getConfidenceLevel() {
        if (isHighConfidence()) return "HIGH";
        if (isMediumConfidence()) return "MEDIUM";
        return "LOW";
    }


    @Override
    public String toString() {
        return String.format(
            "ConfidenceScore{overall=%.2f, level=%s, stack=%.2f, timing=%.2f, pattern=%.2f}",
            overallScore, getConfidenceLevel(), stackTraceScore, timingScore, patternScore);
    }
}
