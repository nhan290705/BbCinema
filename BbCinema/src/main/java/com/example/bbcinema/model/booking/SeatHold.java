package com.example.bbcinema.model.booking;

import com.example.bbcinema.enums.HoldStatus;
import com.example.bbcinema.model.cinema.Seat;
import com.example.bbcinema.model.movie.Showtime;
import com.example.bbcinema.model.user.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "seat_holds", indexes = {
        @Index(name = "idx_seat_hold_lookup", columnList = "showtime_id, seat_id, status, expires_at")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SeatHold {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "showtime_id", nullable = false)
    private Showtime showtime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(name = "held_at", nullable = false)
    private LocalDateTime heldAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt; // heldAt + 5 minutes

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private HoldStatus status = HoldStatus.HOLDING;
}
