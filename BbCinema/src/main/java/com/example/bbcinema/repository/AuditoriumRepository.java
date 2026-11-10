package com.example.bbcinema.repository;

import com.example.bbcinema.model.cinema.Auditorium;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AuditoriumRepository extends JpaRepository<Auditorium, Long> {
    java.util.List<Auditorium> findByCinemaIdOrderByNameAscIdAsc(Long cinemaId);

    boolean existsByCinemaIdAndNameIgnoreCase(Long cinemaId, String name);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from Auditorium a where a.id = :id")
    Optional<Auditorium> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    @Override
    Optional<Auditorium> findById(Long id);
}
