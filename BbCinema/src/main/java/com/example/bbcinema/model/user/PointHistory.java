package com.example.bbcinema.model.user;

import com.example.bbcinema.model.booking.Order;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "point_histories")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PointHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(name = "points_change", nullable = false)
    private Integer pointsChange;

    @Column(name = "balance_after", nullable = false)
    private Integer balanceAfter;

    @Column(nullable = false, length = 50)
    private String reason; // EARN_FROM_ORDER, REDEEM_DISCOUNT, ADJUSTMENT

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}