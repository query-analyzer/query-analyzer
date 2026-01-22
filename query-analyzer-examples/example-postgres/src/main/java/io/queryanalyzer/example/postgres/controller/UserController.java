package io.queryanalyzer.example.postgres.controller;

import io.queryanalyzer.example.postgres.entity.Order;
import io.queryanalyzer.example.postgres.entity.User;
import io.queryanalyzer.example.postgres.repository.OrderRepository;
import io.queryanalyzer.example.postgres.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    public UserController(UserRepository userRepository, OrderRepository orderRepository) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
    }


    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> getAllUsers() {
        List<User> users = userRepository.findAll();

        List<Map<String, Object>> response = users.stream()
                .map(this::convertToMap)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }


    @GetMapping("/optimized")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> getAllUsersOptimized() {
        List<User> users = userRepository.findAllWithOrders();

        List<Map<String, Object>> response = users.stream()
                .map(this::convertToMap)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }


    @GetMapping("/examples/bad/n-plus-one")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> nPlusOneBad() {
        List<User> users = userRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();

        for (User user : users) {
            Map<String, Object> userData = new HashMap<>();
            userData.put("id", user.getId());
            userData.put("name", user.getName());
            userData.put("email", user.getEmail());
            userData.put("orderCount", user.getOrders().size()); // N+1!

            result.add(userData);
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/examples/good/n-plus-one-fixed")
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

    @GetMapping("/examples/bad/multiple-n-plus-one")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> multipleNPlusOne() {
        List<User> users = userRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();

        for (User user : users) {
            Map<String, Object> userData = new HashMap<>();
            userData.put("id", user.getId());
            userData.put("name", user.getName());

            // First N+1: Orders
            userData.put("orderCount", user.getOrders().size());

            // Second N+1: Order details (nested)
            List<String> products = new ArrayList<>();
            for (Order order : user.getOrders()) {
                products.add(order.getProductName());
            }
            userData.put("products", products);

            result.add(userData);
        }

        return ResponseEntity.ok(result);
    }


    @GetMapping("/examples/bad/query-in-loop")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> queryInLoop() {
        List<User> users = userRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();

        for (User user : users) {
            List<Order> orders = orderRepository.findByUserId(user.getId());

            Map<String, Object> data = new HashMap<>();
            data.put("userId", user.getId());
            data.put("userName", user.getName());
            data.put("orderCount", orders.size());

            result.add(data);
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/examples/good/query-in-loop-fixed")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> queryInLoopFixed() {
        List<User> users = userRepository.findAll();

        List<Long> userIds = users.stream()
                .map(User::getId)
                .collect(Collectors.toList());

        List<Order> allOrders = orderRepository.findByUserIdIn(userIds);

        Map<Long, List<Order>> ordersByUser = new HashMap<>();
        for (Order order : allOrders) {
            ordersByUser.computeIfAbsent(order.getUser().getId(), k -> new ArrayList<>())
                    .add(order);
        }

        List<Map<String, Object>> result = new ArrayList<>();
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


    @GetMapping("/examples/good/best-practices")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> bestPractices() {
        List<User> users = userRepository.findAllWithOrders();
        List<Map<String, Object>> result = new ArrayList<>();

        for (User user : users) {
            Map<String, Object> userData = new HashMap<>();
            userData.put("id", user.getId());
            userData.put("name", user.getName());
            userData.put("email", user.getEmail());

            List<Order> orders = user.getOrders();
            userData.put("orderCount", orders.size());

            List<String> products = orders.stream()
                    .map(Order::getProductName)
                    .collect(Collectors.toList());
            userData.put("products", products);

            result.add(userData);
        }

        return ResponseEntity.ok(result);
    }

    private Map<String, Object> convertToMap(User user) {
        List<String> productNames = user.getOrders().stream()
                .map(Order::getProductName)
                .collect(Collectors.toList());

        Map<String, Object> map = new HashMap<>();
        map.put("id", user.getId());
        map.put("name", user.getName());
        map.put("email", user.getEmail());
        map.put("orderCount", user.getOrders().size());
        map.put("products", productNames);

        return map;
    }
}