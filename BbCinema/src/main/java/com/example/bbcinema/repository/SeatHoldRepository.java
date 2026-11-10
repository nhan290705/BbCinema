package com.example.bbcinema.repository;

import com.example.bbcinema.model.booking.SeatHold;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SeatHoldRepository extends JpaRepository<SeatHold, Long> {
    boolean existsBySeatAuditoriumId(Long auditoriumId);
    boolean existsByShowtimeIdAndStatusAndExpiresAtAfter(Long showtimeId,
            com.example.bbcinema.enums.HoldStatus status, java.time.LocalDateTime now);

    @Override
    Optional<SeatHold> findById(Long id);
}
