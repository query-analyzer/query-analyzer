package io.queryanalyzer.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExampleApplication.class, args);

        System.out.println("""
        Query Analyzer Example Application
        ---------------------------------

        BAD Examples (trigger N+1 detection):
          GET /api/examples/bad/n-plus-one
          GET /api/examples/bad/multiple-n-plus-one
          GET /api/examples/bad/query-in-loop
          GET /api/examples/bad/slow-query
          GET /api/examples/bad/everything-wrong

        GOOD Examples (optimized):
          GET /api/examples/good/n-plus-one-fixed
          GET /api/examples/good/query-in-loop-fixed
          GET /api/examples/good/best-practices

        Basic Endpoints:
          GET /api/users        - N+1 pattern (unoptimized)
          GET /api/users/fixed  - JOIN FETCH (optimized)

        Utilities:
          GET /api/examples/parameterized/{id}
          GET /api/examples/test/rate-limit
          GET /h2-console       - H2 Database Console

        Try:
          curl http://localhost:8080/api/examples/bad/n-plus-one

        Watch the console for Query Analyzer output!
        """);
    }
}
