package io.queryanalyzer.example.controller;

import io.queryanalyzer.example.model.Order;
import io.queryanalyzer.example.model.User;
import io.queryanalyzer.example.repository.OrderRepository;
import io.queryanalyzer.example.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api/examples")
public class ExamplesController {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final JdbcTemplate jdbcTemplate;

    public ExamplesController(UserRepository userRepository, OrderRepository orderRepository, JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/bad/n-plus-one")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> nPlusOneBad() {
        List<User> users = userRepository.findAll();
        
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (User user : users) {
            Map<String, Object> userData = new HashMap<>();
            userData.put("id", user.getId());
            userData.put("name", user.getName());
            userData.put("email", user.getEmail());
            
            userData.put("orderCount", user.getOrders().size());
            
            result.add(userData);
        }
        
        return ResponseEntity.ok(result);
    }

    @GetMapping("/good/n-plus-one-fixed")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> nPlusOneGood() {
        List<User> users = userRepository.findAllWithOrders();
        
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (User user : users) {
            Map<String, Object> userData = new HashMap<>();
            userData.put("id", user.getId());
            userData.put("name", user.getName());
            userData.put("email", user.getEmail());
            
            userData.put("orderCount", user.getOrders().size());
            
            result.add(userData);
        }
        
        return ResponseEntity.ok(result);
    }



    @GetMapping("/bad/multiple-n-plus-one")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> multipleNPlusOne() {
        List<User> users = userRepository.findAll();
        
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (User user : users) {
            Map<String, Object> userData = new HashMap<>();
            userData.put("id", user.getId());
            userData.put("name", user.getName());
            
            // This triggers N+1 for orders
            userData.put("orderCount", user.getOrders().size());
            
            // Iterating again uses already-loaded collection (no additional queries)
            List<String> products = new ArrayList<>();
            for (Order order : user.getOrders()) {
                products.add(order.getProductName());
            }
            userData.put("products", products);
            
            result.add(userData);
        }
        
        return ResponseEntity.ok(result);
    }


    @GetMapping("/bad/slow-query")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> slowQuery() {
        // This endpoint demonstrates slow query detection
        // Uses H2's SLEEP alias (created in data.sql) to make the actual SQL query slow
        // CALL SLEEP(500) pauses for 500ms - this is tracked by Query Analyzer
        
        // Execute SLEEP - this takes 500ms and is tracked by Query Analyzer
        jdbcTemplate.execute("CALL SLEEP(500)");
        
        // Then fetch the users (this query is fast)
        List<Map<String, Object>> result = jdbcTemplate.query(
            "SELECT id, name, email FROM users",
            (rs, rowNum) -> {
                Map<String, Object> data = new HashMap<>();
                data.put("id", rs.getLong("id"));
                data.put("name", rs.getString("name"));
                data.put("email", rs.getString("email"));
                return data;
            }
        );
        
        return ResponseEntity.ok(result);
    }

    @GetMapping("/bad/query-in-loop")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> queryInLoop() {
        List<User> users = userRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (User user : users) {
            // BAD: Querying in a loop!
            List<Order> orders = orderRepository.findByUserId(user.getId());
            
            Map<String, Object> data = new HashMap<>();
            data.put("userId", user.getId());
            data.put("userName", user.getName());
            data.put("orderCount", orders.size());
            
            result.add(data);
        }
        
        return ResponseEntity.ok(result);
    }
    

    @GetMapping("/good/query-in-loop-fixed")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> queryInLoopFixed() {
        List<User> users = userRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();
        
        // GOOD: Load all orders at once
        List<Long> userIds = users.stream()
            .map(User::getId)
            .toList();
        
        List<Order> allOrders = orderRepository.findByUserIdIn(userIds);
        
        // Group orders by user
        Map<Long, List<Order>> ordersByUser = new HashMap<>();
        for (Order order : allOrders) {
            ordersByUser.computeIfAbsent(order.getUser().getId(), k -> new ArrayList<>())
                .add(order);
        }
        
        for (User user : users) {
            Map<String, Object> data = new HashMap<>();
            data.put("userId", user.getId());
            data.put("userName", user.getName());
            
            List<Order> userOrders = ordersByUser.getOrDefault(user.getId(), List.of());
            data.put("orderCount", userOrders.size());
            
            result.add(data);
        }
        
        return ResponseEntity.ok(result);
    }

    @GetMapping("/parameterized/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> parameterizedQuery(@PathVariable("id") Long id) {
        // This query has parameters: WHERE user_id = ?
        User user = userRepository.findById(id).orElse(null);
        
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        
        // This triggers N+1 with parameterized query
        List<Order> orders = user.getOrders();
        
        Map<String, Object> result = new HashMap<>();
        result.put("user", user.getName());
        result.put("orders", orders.size());
        
        return ResponseEntity.ok(result);
    }

    /**
     * Combines multiple anti-patterns to trigger multiple issues:
     * - N+1 query pattern (orders)
     * - Slow query (via SLEEP)
     */
    @GetMapping("/bad/everything-wrong")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> everythingWrong() {
        List<Map<String, Object>> result = new ArrayList<>();
        
        // Anti-pattern 1: Trigger a slow query first
        jdbcTemplate.execute("CALL SLEEP(200)");
        
        // Anti-pattern 2: Load all users (could be thousands)
        List<User> users = userRepository.findAll();
        
        for (User user : users) {
            Map<String, Object> userData = new HashMap<>();
            
            // Anti-pattern 3: N+1 on orders
            List<Order> orders = user.getOrders();
            
            // Anti-pattern 4: Nested iteration (but same collection, so no extra queries)
            for (Order order : orders) {
                userData.put("order_" + order.getId(), order.getProductName());
            }
            
            userData.put("userId", user.getId());
            userData.put("orderCount", orders.size());
            
            result.add(userData);
        }
        
        return ResponseEntity.ok(result);
    }

    @GetMapping("/good/best-practices")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> bestPractices() {
        // Best practice 1: Use JOIN FETCH to load everything in one query
        List<User> users = userRepository.findAllWithOrders();
        
        // Best practice 2: Process in memory (no additional queries)
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (User user : users) {
            Map<String, Object> userData = new HashMap<>();
            userData.put("id", user.getId());
            userData.put("name", user.getName());
            userData.put("email", user.getEmail());
            
            // Orders already loaded - no query!
            List<Order> orders = user.getOrders();
            userData.put("orderCount", orders.size());
            
            // Process orders in memory
            List<String> products = orders.stream()
                .map(Order::getProductName)
                .toList();
            userData.put("products", products);
            
            result.add(userData);
        }
        
        return ResponseEntity.ok(result);
    }

    @GetMapping("/test/rate-limit")
    @Transactional(readOnly = true)
    public ResponseEntity<String> testRateLimit() {
        // Trigger N+1 to generate ERROR that triggers plan analysis
        List<User> users = userRepository.findAll();
        
        for (User user : users) {
            user.getOrders().size(); // N+1!
        }
        
        return ResponseEntity.ok("Check console for rate limiting");
    }
}
