package io.queryanalyzer.example;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;


@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Example Endpoint Integration Tests")
class ExampleEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/users returns users with N+1")
    void testGetAllUsers() throws Exception {
        mockMvc.perform(get("/api/users"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(greaterThan(0))))
            .andExpect(jsonPath("$[0].id").exists())
            .andExpect(jsonPath("$[0].name").exists())
            .andExpect(jsonPath("$[0].email").exists())
            .andExpect(jsonPath("$[0].orderCount").exists());
    }

    @Test
    @DisplayName("GET /api/users/fixed returns users without N+1")
    void testGetAllUsersOptimized() throws Exception {
        mockMvc.perform(get("/api/users/fixed"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(greaterThan(0))))
            .andExpect(jsonPath("$[0].id").exists())
            .andExpect(jsonPath("$[0].name").exists());
    }


    @Test
    @DisplayName("GET /api/examples/bad/n-plus-one triggers N+1 detection")
    void testNPlusOneBad() throws Exception {
        mockMvc.perform(get("/api/examples/bad/n-plus-one"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(10)))
            .andExpect(jsonPath("$[0].orderCount").exists());
    }

    @Test
    @DisplayName("GET /api/examples/bad/multiple-n-plus-one triggers multiple issues")
    void testMultipleNPlusOne() throws Exception {
        mockMvc.perform(get("/api/examples/bad/multiple-n-plus-one"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].products").isArray());
    }

    @Test
    @DisplayName("GET /api/examples/bad/query-in-loop triggers detection")
    void testQueryInLoop() throws Exception {
        mockMvc.perform(get("/api/examples/bad/query-in-loop"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].userId").exists())
            .andExpect(jsonPath("$[0].orderCount").exists());
    }

    @Test
    @DisplayName("GET /api/examples/bad/slow-query simulates slow query")
    void testSlowQuery() throws Exception {
        long startTime = System.currentTimeMillis();
        
        mockMvc.perform(get("/api/examples/bad/slow-query"))
            .andExpect(status().isOk());
        
        long elapsed = System.currentTimeMillis() - startTime;
        // Should take at least 500ms (simulated delay)
        assert elapsed >= 500 : "Slow query should take time";
    }

    @Test
    @DisplayName("GET /api/examples/bad/everything-wrong combines all anti-patterns")
    void testEverythingWrong() throws Exception {
        mockMvc.perform(get("/api/examples/bad/everything-wrong"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/examples/good/n-plus-one-fixed uses JOIN FETCH")
    void testNPlusOneFixed() throws Exception {
        mockMvc.perform(get("/api/examples/good/n-plus-one-fixed"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(10)))
            .andExpect(jsonPath("$[0].orderCount").exists());
    }

    @Test
    @DisplayName("GET /api/examples/good/query-in-loop-fixed uses batch loading")
    void testQueryInLoopFixed() throws Exception {
        mockMvc.perform(get("/api/examples/good/query-in-loop-fixed"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].orderCount").exists());
    }

    @Test
    @DisplayName("GET /api/examples/good/best-practices demonstrates optimal code")
    void testBestPractices() throws Exception {
        mockMvc.perform(get("/api/examples/good/best-practices"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].products").isArray());
    }

    @Test
    @DisplayName("GET /api/examples/parameterized/{id} handles parameters")
    void testParameterizedQuery() throws Exception {
        mockMvc.perform(get("/api/examples/parameterized/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user").exists())
            .andExpect(jsonPath("$.orders").exists());
    }

    @Test
    @DisplayName("GET /api/examples/parameterized/{id} returns 404 for missing user")
    void testParameterizedQueryNotFound() throws Exception {
        mockMvc.perform(get("/api/examples/parameterized/99999"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/examples/test/rate-limit triggers rate limiting")
    void testRateLimit() throws Exception {
        // Make multiple requests to trigger rate limiting
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/examples/test/rate-limit"))
                .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("Compare response times: BAD vs GOOD endpoints")
    void testCompareResponseTimes() throws Exception {
        // Warm up
        mockMvc.perform(get("/api/examples/good/n-plus-one-fixed"));
        mockMvc.perform(get("/api/examples/bad/n-plus-one"));
        
        // Measure GOOD endpoint
        long goodStart = System.currentTimeMillis();
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/examples/good/n-plus-one-fixed"));
        }
        long goodTime = System.currentTimeMillis() - goodStart;
        
        // Measure BAD endpoint
        long badStart = System.currentTimeMillis();
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/examples/bad/n-plus-one"));
        }
        long badTime = System.currentTimeMillis() - badStart;
        
        System.out.println("Performance Comparison:");
        System.out.println("  GOOD endpoint (3 requests): " + goodTime + "ms");
        System.out.println("  BAD endpoint (3 requests): " + badTime + "ms");
        
    }
}
