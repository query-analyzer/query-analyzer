package io.queryanalyzer.core.reporter;

import io.queryanalyzer.core.model.Severity;
import lombok.Data;

import java.io.PrintWriter;


@Data
public class ReporterConfig {

    private boolean colorEnabled = true;
    private boolean printSuggestions = true;
    private boolean printMetrics = true;
    private Severity minimumSeverity = Severity.INFO;
    private PrintWriter output;

    public boolean shouldUseColors() {
        if (!colorEnabled) {
            return false;
        }

        if (isCI()) return false;

        return System.console() != null;
    }


    private boolean isCI() {
        return System.getenv("CI") != null ||
            System.getenv("JENKINS_HOME") != null ||
            System.getenv("GITHUB_ACTIONS") != null ||
            System.getenv("GITLAB_CI") != null;
    }
}
