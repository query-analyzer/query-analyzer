package io.queryanalyzer.benchmark;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Boot configuration anchor for the benchmark. Required so that @DataJpaTest can
 * locate a @SpringBootConfiguration and component-scan the entities/repositories.
 */
@SpringBootApplication
public class BenchmarkApplication {
    public static void main(String[] args) {
        SpringApplication.run(BenchmarkApplication.class, args);
    }
}
