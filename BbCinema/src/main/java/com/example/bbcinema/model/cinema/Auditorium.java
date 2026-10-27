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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AuditoriumStatus status = AuditoriumStatus.ACTIVE;
}
