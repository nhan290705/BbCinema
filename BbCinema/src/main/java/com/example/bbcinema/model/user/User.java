package com.example.bbcinema.model.user;

import com.example.bbcinema.enums.UserStatus;
import com.example.bbcinema.model.cinema.Cinema;
import com.example.bbcinema.model.cinema.Province;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tier_id")
    private MembershipTier tier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_cinema_id")
    private Cinema assignedCinema;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, unique = true, length = 120)
    private String email;

    @Column(nullable = false, unique = true, length = 20)
    private String phone;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    private LocalDate dob;

    @Column(name = "current_points", nullable = false)
    private Integer currentPoints = 0;

    @Column(name = "last_lat", precision = 10, scale = 7)
    private BigDecimal lastLat;

    @Column(name = "last_lng", precision = 10, scale = 7)
    private BigDecimal lastLng;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preferred_province_id")
    private Province preferredProvince;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserStatus status = UserStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
