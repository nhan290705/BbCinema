package com.example.bbcinema.repository;

import com.example.bbcinema.model.booking.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    boolean existsByShowtimeId(Long showtimeId);

    boolean existsBySeatAuditoriumId(Long auditoriumId);

    @Override
    Optional<Ticket> findById(Long id);
}
