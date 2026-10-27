package com.example.bbcinema.model.cinema;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "seat_types")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SeatType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "type_name", nullable = false, unique = true, length = 50)
    private String typeName; // STANDARD, VIP, COUPLE

    @Column(name = "default_surcharge", precision = 12, scale = 2, nullable = false)
    private BigDecimal defaultSurcharge = BigDecimal.ZERO;

    @Column(name = "seat_span", nullable = false)
    private Integer seatSpan = 1; // 1: đơn, 2: đôi
}