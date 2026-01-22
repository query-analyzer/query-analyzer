package io.queryanalyzer.example.service;

import io.queryanalyzer.example.dto.UserDTO;
import io.queryanalyzer.example.model.Order;
import io.queryanalyzer.example.model.User;
import io.queryanalyzer.example.repository.OrderRepository;
import io.queryanalyzer.example.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service
public class UserService {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    public UserService(UserRepository userRepository, OrderRepository orderRepository) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public List<UserDTO> getAllUsersWithOrdersBad() {
        List<User> users = userRepository.findAll();
        
        return users.stream()
            .map(user -> {
                List<String> products = user.getOrders().stream()
                    .map(Order::getProductName)
                    .collect(Collectors.toList());
                    
                return new UserDTO(
                    user.getId(),
                    user.getName(),
                    user.getEmail(),
                    user.getOrders().size(),
                    products
                );
            })
            .collect(Collectors.toList());
    }


    @Transactional(readOnly = true)
    public Map<Long, Integer> getUserOrderCountsBad() {
        List<User> users = userRepository.findAll();
        
        Map<Long, Integer> result = new HashMap<>();
        
        for (User user : users) {
            List<Order> orders = orderRepository.findByUserId(user.getId());
            result.put(user.getId(), orders.size());
        }
        
        return result;
    }


    @Transactional(readOnly = true)
    public List<Map<String, Object>> getFullReportBad() {
        List<User> users = userRepository.findAll();
        List<Map<String, Object>> report = new ArrayList<>();
        
        for (User user : users) {
            Map<String, Object> row = new HashMap<>();
            row.put("userId", user.getId());
            row.put("userName", user.getName());
            
            // N+1 #1: Access orders
            List<Order> orders = user.getOrders();
            row.put("orderCount", orders.size());
            
            // N+1 #2: Process each order
            double totalAmount = 0;
            List<String> products = new ArrayList<>();
            for (Order order : orders) {
                totalAmount += order.getAmount().doubleValue();
                products.add(order.getProductName());
            }
            
            row.put("totalAmount", totalAmount);
            row.put("products", products);
            
            report.add(row);
        }
        
        return report;
    }


    @Transactional(readOnly = true)
    public List<UserDTO> getAllUsersWithOrdersGood() {
        // Single query with JOIN FETCH
        List<User> users = userRepository.findAllWithOrders();
        
        // All data already loaded - no additional queries!
        return users.stream()
            .map(user -> {
                List<String> products = user.getOrders().stream()
                    .map(Order::getProductName)
                    .collect(Collectors.toList());
                    
                return new UserDTO(
                    user.getId(),
                    user.getName(),
                    user.getEmail(),
                    user.getOrders().size(),
                    products
                );
            })
            .collect(Collectors.toList());
    }


    @Transactional(readOnly = true)
    public Map<Long, Integer> getUserOrderCountsGood() {
        // Query 1: Load all users
        List<User> users = userRepository.findAll();
        
        // Query 2: Batch load all orders
        List<Long> userIds = users.stream()
            .map(User::getId)
            .collect(Collectors.toList());
        
        List<Order> allOrders = orderRepository.findByUserIdIn(userIds);
        
        // Group in memory
        Map<Long, List<Order>> ordersByUser = allOrders.stream()
            .collect(Collectors.groupingBy(o -> o.getUser().getId()));
        
        // Build result in memory - no additional queries!
        Map<Long, Integer> result = new HashMap<>();
        for (User user : users) {
            List<Order> userOrders = ordersByUser.getOrDefault(user.getId(), List.of());
            result.put(user.getId(), userOrders.size());
        }
        
        return result;
    }


    @Transactional(readOnly = true)
    public List<Map<String, Object>> getFullReportGood() {
        // Single query loads everything
        List<User> users = userRepository.findAllWithOrders();
        
        List<Map<String, Object>> report = new ArrayList<>();
        
        for (User user : users) {
            Map<String, Object> row = new HashMap<>();
            row.put("userId", user.getId());
            row.put("userName", user.getName());
            
            // Orders already loaded - no query!
            List<Order> orders = user.getOrders();
            row.put("orderCount", orders.size());
            
            // Process in memory
            double totalAmount = orders.stream()
                .mapToDouble(o -> o.getAmount().doubleValue())
                .sum();
            
            List<String> products = orders.stream()
                .map(Order::getProductName)
                .collect(Collectors.toList());
            
            row.put("totalAmount", totalAmount);
            row.put("products", products);
            
            report.add(row);
        }
        
        return report;
    }


    @Transactional(readOnly = true)
    public User getUserById(Long id) {
        return userRepository.findById(id).orElse(null);
    }

    public long countUsers() {
        return userRepository.count();
    }
}
