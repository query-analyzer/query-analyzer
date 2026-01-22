package io.queryanalyzer.core.config;


public enum DetectionProfile {
    

    BALANCED("Balanced detection - recommended default for most applications"),
    

    STRICT("Strict detection - aggressive, catches more issues, may have false positives"),
    
    LENIENT("Lenient detection - fewer false positives, suitable for production and batch jobs");
    
    private final String description;
    
    DetectionProfile(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}
