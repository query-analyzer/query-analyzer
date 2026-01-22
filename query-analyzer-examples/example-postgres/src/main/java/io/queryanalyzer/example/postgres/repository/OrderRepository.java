package io.queryanalyzer.example.postgres.repository;

import io.queryanalyzer.example.postgres.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUserId(Long userId);

    List<Order> findByUserIdIn(List<Long> userIds);
}