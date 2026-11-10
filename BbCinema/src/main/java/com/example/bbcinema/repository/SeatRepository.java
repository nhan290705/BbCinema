package com.example.bbcinema.repository;

import com.example.bbcinema.model.cinema.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SeatRepository extends JpaRepository<Seat, Long> {
    boolean existsByAuditoriumId(Long auditoriumId);

    java.util.List<Seat> findByAuditoriumIdOrderByGridYAscGridXAsc(Long auditoriumId);

    Optional<Seat> findByIdAndAuditoriumId(Long id, Long auditoriumId);

    @Override
    Optional<Seat> findById(Long id);
}
