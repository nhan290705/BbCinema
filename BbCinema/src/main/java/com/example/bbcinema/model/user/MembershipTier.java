package com.example.bbcinema.model.user;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "membership_tiers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MembershipTier {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "tier_name", nullable = false, unique = true, length = 50)
    private String tierName; // Member, Silver, Gold

    @Column(name = "min_points_required", nullable = false)
    private Integer minPointsRequired = 0;

    @Column(name = "points_earn_rate", precision = 5, scale = 2, nullable = false)
    private BigDecimal pointsEarnRate;

    @Column(name = "discount_percent", precision = 5, scale = 2)
    private BigDecimal discountPercent = BigDecimal.ZERO;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}