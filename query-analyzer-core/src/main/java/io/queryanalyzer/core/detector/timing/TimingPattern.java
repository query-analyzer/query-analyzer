package io.queryanalyzer.core.detector.timing;

import io.queryanalyzer.core.config.DetectorConfig;


public enum TimingPattern {
    
    TIGHT_LOOP("Queries executed in tight loop", true),
    MODERATE_LOOP("Queries executed in moderate loop", true),
    SLOW_LOOP("Queries executed in slow loop", false),
    DELIBERATE_PACING("Queries deliberately paced (rate limiting detected)", false),
    UNKNOWN("Timing pattern unclear", false);
    
    private final String description;
    private final boolean suspicious;
    
    TimingPattern(String description, boolean suspicious) {
        this.description = description;
        this.suspicious = suspicious;
    }
    
    public String getDescription() {
        return description;
    }
    
    public boolean isSuspicious() {
        return suspicious;
    }
    


    public static TimingPattern fromTotalTime(long totalTimeMs, DetectorConfig config) {
        if (totalTimeMs < config.getTightLoopThresholdMs()) {
            return TIGHT_LOOP;
        } else if (totalTimeMs < config.getModerateLoopThresholdMs()) {
            return MODERATE_LOOP;
        } else if (totalTimeMs < config.getSlowLoopThresholdMs()) {
            return SLOW_LOOP;
        } else {
            return UNKNOWN;
        }
    }
}
