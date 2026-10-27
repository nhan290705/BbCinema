package com.example.bbcinema.repository;

import com.example.bbcinema.model.booking.OrderProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface OrderProductRepository
        extends JpaRepository<OrderProduct, Long> {

    @Override
    Optional<OrderProduct> findById(Long id);
}
