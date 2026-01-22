package io.queryanalyzer.example.controller;

import io.queryanalyzer.example.dto.UserDTO;
import io.queryanalyzer.example.model.Order;
import io.queryanalyzer.example.model.User;
import io.queryanalyzer.example.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        List<User> users = userRepository.findAll();

        List<UserDTO> response = users.stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }


    @GetMapping("/fixed")
    public ResponseEntity<List<UserDTO>> getAllUsersOptimized() {
        List<User> users = userRepository.findAllWithOrders();

        List<UserDTO> response = users.stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }


    private UserDTO convertToDTO(User user) {
        List<String> productNames = user.getOrders().stream()
            .map(Order::getProductName)
            .collect(Collectors.toList());

        return new UserDTO(
            user.getId(),
            user.getName(),
            user.getEmail(),
            user.getOrders().size(),
            productNames
        );
    }
}
