package com.example.bbcinema.repository;

import com.example.bbcinema.model.booking.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Override
    Optional<Order> findById(Long id);
}