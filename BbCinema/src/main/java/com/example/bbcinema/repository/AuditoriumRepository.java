package com.example.bbcinema.repository;

import com.example.bbcinema.model.cinema.Auditorium;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AuditoriumRepository extends JpaRepository<Auditorium, Long> {

    @Override
    Optional<Auditorium> findById(Long id);
}
