package io.queryanalyzer.spring.config;

import io.queryanalyzer.core.config.DetectionProfile;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;


@ConfigurationProperties(prefix = "query-analyzer")
@Validated
@Data
public class QueryAnalyzerProperties {

    private boolean enabled = true;
    

    @NotNull
    private DetectionProfile profile = DetectionProfile.BALANCED;

    @Valid
    @NotNull
    private Detection detection = new Detection();

    @Valid
    @NotNull
    private Thresholds thresholds = new Thresholds();

    @Valid
    @NotNull
    private Reporter reporter = new Reporter();
    
    @Valid
    @NotNull
    private PlanAnalysis plan = new PlanAnalysis();



    @Data
    public static class Detection {

        private boolean nPlusOne = true;


        private boolean slowQueries = true;


        private Double minConfidence = null;


        private boolean showConfidence = true;
        
        /**
         * Detection mode: THRESHOLD, CONFIDENCE, or HYBRID
         */
        private String mode = null;
        

        @Valid
        private AdvancedConfig advanced = null;
    }
    

    @Data
    public static class AdvancedConfig {
        

        private Integer minRepetitions;
        

        private Integer maxQueries;

        @Valid
        private ConfidenceWeights weights;

        @Valid
        private TimingConfig timing;

        @Valid
        private SeverityThresholds severity;
    }
    
    @Data
    public static class ConfidenceWeights {

        private Double stackTrace;
        

        private Double timing;
        

        private Double pattern;
    }
    
    @Data
    public static class TimingConfig {

        private Long deliberatePacingMs;
        

        private Integer minSamplesForVariance;
        

        private Double maxCoefficientOfVariation;
        

        private Long tightLoopMs;


        private Long moderateLoopMs;

        private Long slowLoopMs;
    }
    
    @Data
    public static class SeverityThresholds {

        private Long warningTimeMs;
        private Integer warningQueryCount;
        

        private Long errorTimeMs;
        private Integer errorQueryCount;
        

        private Long criticalTimeMs;
        private Integer criticalQueryCount;
    }



    @Data
    public static class Thresholds {

        @Min(0)
        private long info = 50;


        @Min(0)
        private long warning = 200;


        @Min(0)
        private long error = 500;


        @Min(0)
        private long critical = 2000;
    }



    @Data
    public static class Reporter {

        private boolean colors = true;

        private boolean suggestions = true;

        private boolean metrics = true;

        @NotNull
        private String minimumSeverity = "INFO";
    }


    @Data
    public static class PlanAnalysis {

        private boolean enabled = true;
        

        @Min(1)
        private int maxPerRequest = 3;
        

        @Min(1)
        private int timeoutSeconds = 2;

        @NotNull
        private String minSeverity = "ERROR";

        @Min(1)
        private int maxPerMinute = 60;
    }


    @Data
    public static class Metrics {

        private boolean enabled = false;


        private String exporter = "prometheus";


        private String endpoint = "/actuator/query-analyzer/metrics";
    }

    @Valid
    private Metrics metrics = new Metrics();

    public Metrics getMetrics() {
        return metrics;
    }

    public void setMetrics(Metrics metrics) {
        this.metrics = metrics;
    }
}
