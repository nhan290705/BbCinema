package com.example.bbcinema.repository;

import com.example.bbcinema.model.movie.ShowtimePrice;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ShowtimePriceRepository
        extends JpaRepository<ShowtimePrice, Long> {

    @Override
    Optional<ShowtimePrice> findById(Long id);
}
