package com.example.bbcinema.repository;

import com.example.bbcinema.model.fnb.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    @Override
    Optional<Promotion> findById(Long id);
}