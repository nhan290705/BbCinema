package com.example.bbcinema.repository;

import com.example.bbcinema.model.fnb.CinemaInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CinemaInventoryRepository
        extends JpaRepository<CinemaInventory, Long> {

    @Override
    Optional<CinemaInventory> findById(Long id);
}
