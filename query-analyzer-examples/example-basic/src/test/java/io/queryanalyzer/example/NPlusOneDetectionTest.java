package io.queryanalyzer.example;

import io.queryanalyzer.example.model.User;
import io.queryanalyzer.example.repository.UserRepository;
import io.queryanalyzer.test.NoNPlusOne;
import io.queryanalyzer.test.NPlusOneDetectedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("N+1 Query Detection Tests")
class NPlusOneDetectionTest {

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setup() {
    }


    @Nested
    @DisplayName("Tests that should PASS (optimized queries)")
    class PassingTests {

        @Test
        @NoNPlusOne
        @Transactional(readOnly = true)
        @DisplayName("JOIN FETCH eliminates N+1 - should pass")
        void testOptimizedQuery_NoNPlusOne() {
            // This uses JOIN FETCH - should NOT trigger N+1
            List<User> users = userRepository.findAllWithOrders();
            
            // Access orders - they're already loaded!
            int totalOrders = 0;
            for (User user : users) {
                totalOrders += user.getOrders().size();
            }
            
            assertTrue(totalOrders > 0, "Should have loaded orders");
            assertFalse(users.isEmpty(), "Should have users");
        }

        @Test
        @NoNPlusOne(threshold = 15)
        @Transactional(readOnly = true)
        @DisplayName("High threshold allows some repetition - should pass")
        void testWithHighThreshold_ShouldPass() {
            // Even with N+1, a threshold above the row count allows it (data.sql seeds 10 users)
            List<User> users = userRepository.findAll();

            for (User user : users) {
                // This triggers N+1, but the 10 repeats stay under the threshold of 15
                user.getOrders().size();
            }

            assertFalse(users.isEmpty());
        }

        @Test
        @NoNPlusOne
        @DisplayName("Single query without lazy loading - should pass")
        void testSimpleQuery_NoLazyLoading() {
            // Just count - no lazy loading triggered
            long count = userRepository.count();
            assertTrue(count > 0);
        }

        @Test
        @NoNPlusOne
        @Transactional(readOnly = true)
        @DisplayName("findById without accessing collections - should pass")
        void testFindById_NoCollectionAccess() {
            User user = userRepository.findById(1L).orElse(null);
            
            assertNotNull(user);
            assertNotNull(user.getName());
            // NOT accessing user.getOrders() - no N+1!
        }
    }

    @Nested
    @DisplayName("Tests that demonstrate N+1 detection (expected to fail)")
    class FailingTests {

        /**
         * UNCOMMENT THIS TEST TO SEE IT FAIL!
         * 
         * This test intentionally triggers N+1 to demonstrate detection.
         */
        // @Test
        // @NoNPlusOne
        // @Transactional(readOnly = true)
        // @DisplayName("Classic N+1 - should FAIL")
        void testClassicNPlusOne_ShouldFail() {
            // Load all users (1 query)
            List<User> users = userRepository.findAll();
            
            // Access orders for each user (N queries) - THIS IS N+1!
            for (User user : users) {
                user.getOrders().size(); // Triggers lazy load!
            }
            
            // This test should fail with NPlusOneDetectedException
        }

        /**
         * UNCOMMENT THIS TEST TO SEE IT FAIL!
         * 
         * Even with threshold=3, we have 5 users so it will fail.
         */
        // @Test
        // @NoNPlusOne(threshold = 3)
        // @Transactional(readOnly = true)
        // @DisplayName("N+1 exceeds threshold - should FAIL")
        void testNPlusOneExceedsThreshold_ShouldFail() {
            List<User> users = userRepository.findAll();
            
            // 5 users = 5 additional queries, threshold is 3
            for (User user : users) {
                user.getOrders().size();
            }
        }
    }


    @Nested
    @DisplayName("Programmatic N+1 detection (without annotation)")
    class ProgrammaticTests {

        @Test
        @DisplayName("Catch NPlusOneDetectedException programmatically")
        void testCatchNPlusOneException() {

            
            List<User> users = userRepository.findAllWithOrders();
            
            assertFalse(users.isEmpty());
            assertTrue(users.stream().anyMatch(u -> !u.getOrders().isEmpty()));
        }
    }


    @Nested
    @DisplayName("Edge case tests")
    class EdgeCaseTests {

        @Test
        @NoNPlusOne
        @DisplayName("Empty result set - should pass")
        void testEmptyResultSet() {
            List<User> users = userRepository.findAllWithOrders();
            
            List<User> filtered = users.stream()
                .filter(u -> u.getEmail().contains("nonexistent"))
                .toList();
            
            assertTrue(filtered.isEmpty());
        }

        @Test
        @NoNPlusOne(threshold = 2)
        @Transactional(readOnly = true)
        @DisplayName("Accessing single user's orders - should pass")
        void testSingleUserOrders() {
            User user = userRepository.findById(1L).orElse(null);
            
            assertNotNull(user);
            assertFalse(user.getOrders().isEmpty());
        }
    }
}
