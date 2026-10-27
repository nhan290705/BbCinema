package com.example.bbcinema.repository;

import com.example.bbcinema.model.booking.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Override
    Optional<Payment> findById(Long id);
}
