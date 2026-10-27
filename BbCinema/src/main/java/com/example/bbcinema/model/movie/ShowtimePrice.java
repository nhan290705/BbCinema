package com.example.bbcinema.model.movie;

import com.example.bbcinema.model.cinema.SeatType;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "showtime_prices", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"showtime_id", "seat_type_id"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ShowtimePrice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "showtime_id", nullable = false)
    private Showtime showtime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_type_id", nullable = false)
    private SeatType seatType;

    @Column(precision = 12, scale = 2, nullable = false)
    private BigDecimal price;
}
