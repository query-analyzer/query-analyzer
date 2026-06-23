package io.queryanalyzer.benchmark.repository;

import io.queryanalyzer.benchmark.model.Order;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUserId(Long userId);

    /** Batched fetch via a single IN (...) statement. */
    List<Order> findByUserIdIn(List<Long> userIds);

    /** Timestamp-filtered read, used to model a streaming/polling workload. */
    List<Order> findByOrderDateAfter(LocalDateTime threshold);

    /**
     * Returns a List (not a Page) with a Pageable so Spring Data emits ONLY the
     * windowed SELECT (limit/offset) and no extra COUNT query. Used to model a
     * legitimate pagination loop.
     */
    List<Order> findByProductNameNotNull(Pageable pageable);
}
