package com.example.bbcinema.repository;

import com.example.bbcinema.model.movie.Showtime;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ShowtimeRepository extends JpaRepository<Showtime, Long> {

    @Override
    Optional<Showtime> findById(Long id);
}
