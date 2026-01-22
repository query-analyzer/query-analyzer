package io.queryanalyzer.spring.autoconfigure;

import io.queryanalyzer.core.detector.NPlusOneDetector;
import io.queryanalyzer.core.detector.SlowQueryDetector;
import io.queryanalyzer.spring.config.QueryAnalyzerProperties;
import io.queryanalyzer.spring.filter.QueryAnalysisFilter;
import io.queryanalyzer.spring.service.QueryAnalysisOrchestrator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class QueryAnalyzerAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            DataSourceAutoConfiguration.class,
            QueryAnalyzerAutoConfiguration.class
        ))
        .withPropertyValues(
            "spring.datasource.url=jdbc:h2:mem:testdb",
            "spring.datasource.driver-class-name=org.h2.Driver"
        );

    @Test
    void shouldAutoConfigureWhenEnabled() {
        contextRunner
            .withPropertyValues("query-analyzer.enabled=true")
            .run(context -> {
                assertThat(context).hasSingleBean(QueryAnalyzerProperties.class);
                assertThat(context).hasSingleBean(NPlusOneDetector.class);
                assertThat(context).hasSingleBean(SlowQueryDetector.class);
                assertThat(context).hasSingleBean(QueryAnalysisOrchestrator.class);
                assertThat(context).hasSingleBean(DataSource.class);
            });
    }

    @Test
    void shouldNotAutoConfigureWhenDisabled() {
        contextRunner
            .withPropertyValues("query-analyzer.enabled=false")
            .run(context -> {
                assertThat(context).doesNotHaveBean(NPlusOneDetector.class);
                assertThat(context).doesNotHaveBean(SlowQueryDetector.class);
            });
    }

    @Test
    void shouldConfigureWithCustomThresholds() {
        contextRunner
            .withPropertyValues(
                "query-analyzer.thresholds.warning=100",
                "query-analyzer.thresholds.error=300"
            )
            .run(context -> {
                QueryAnalyzerProperties props = context.getBean(QueryAnalyzerProperties.class);
                assertThat(props.getThresholds().getWarning()).isEqualTo(100);
                assertThat(props.getThresholds().getError()).isEqualTo(300);
            });
    }

    @Test
    void shouldDisableSpecificDetectors() {
        contextRunner
            .withPropertyValues("query-analyzer.detection.n-plus-one=false")
            .run(context -> {
                assertThat(context).doesNotHaveBean(NPlusOneDetector.class);
                assertThat(context).hasSingleBean(SlowQueryDetector.class);
            });
    }

    @Test
    void shouldWrapDataSource() {
        contextRunner.run(context -> {
            DataSource dataSource = context.getBean(DataSource.class);
            assertThat(dataSource).isNotNull();
            // Verify it's wrapped (proxy check)
            assertThat(dataSource.getClass().getName()).contains("Proxy");
        });
    }
}
