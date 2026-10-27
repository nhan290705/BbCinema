package com.example.bbcinema.repository;

import com.example.bbcinema.model.cinema.Cinema;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CinemaRepository extends JpaRepository<Cinema, Long> {

    @Override
    Optional<Cinema> findById(Long id);
}
