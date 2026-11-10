package com.example.bbcinema.model.cinema;

import com.example.bbcinema.enums.AuditoriumStatus;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "auditoriums")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Auditorium {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cinema_id", nullable = false)
    private Cinema cinema;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "total_rows", nullable = false)
    private Integer totalRows;

    @Column(name = "total_cols", nullable = false)
    private Integer totalCols;

    @Column(name = "seat_width")
    private Integer seatWidth;

    @Column(name = "seat_height")
    private Integer seatHeight;

    @Column(name = "column_gap")
    private Integer columnGap;

    @Column(name = "row_gap")
    private Integer rowGap;

    @Column(name = "screen_position", length = 10)
    private String screenPosition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AuditoriumStatus status = AuditoriumStatus.ACTIVE;
}
