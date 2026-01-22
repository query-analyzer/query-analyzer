package io.queryanalyzer.example.postgres;

import io.queryanalyzer.example.postgres.entity.Order;
import io.queryanalyzer.example.postgres.entity.User;
import io.queryanalyzer.example.postgres.repository.OrderRepository;
import io.queryanalyzer.example.postgres.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    public DataInitializer(UserRepository userRepository, OrderRepository orderRepository) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
    }

    @Override
    public void run(String... args) {
        System.out.println("Initializing database with sample data...");

        // Create 10 users
        for (int i = 1; i <= 10; i++) {
            User user = new User("User " + i, "user" + i + "@example.com");
            userRepository.save(user);

            // Create 3-5 orders per user
            int orderCount = 3 + (i % 3);
            for (int j = 1; j <= orderCount; j++) {
                Order order = new Order(
                        "Product " + (i * 10 + j),
                        BigDecimal.valueOf(10.00 + (i * j)),
                        user
                );
                orderRepository.save(order);
            }
        }

        System.out.println("Database initialized with 10 users and their orders");
    }
}