package com.example.bbcinema.repository;

import com.example.bbcinema.model.cinema.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    @Override
    Optional<Seat> findById(Long id);
}
