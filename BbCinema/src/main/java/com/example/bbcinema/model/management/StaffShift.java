package com.example.bbcinema.model.management;

import com.example.bbcinema.enums.ShiftStatus;
import com.example.bbcinema.model.cinema.Cinema;
import com.example.bbcinema.model.user.User;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "staff_shifts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StaffShift {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cinema_id", nullable = false)
    private Cinema cinema;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", nullable = false)
    private User staff;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by_manager_id", nullable = false)
    private User assignedByManager;

    @Column(name = "shift_start", nullable = false)
    private LocalDateTime shiftStart;

    @Column(name = "shift_end", nullable = false)
    private LocalDateTime shiftEnd;

    @Column(name = "opening_cash", precision = 12, scale = 2)
    private BigDecimal openingCash = BigDecimal.ZERO;

    @Column(name = "closing_cash", precision = 12, scale = 2)
    private BigDecimal closingCash;

    @Column(name = "total_pos_revenue", precision = 12, scale = 2)
    private BigDecimal totalPosRevenue = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ShiftStatus status = ShiftStatus.SCHEDULED;
}