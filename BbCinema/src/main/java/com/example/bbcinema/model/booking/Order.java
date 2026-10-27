package com.example.bbcinema.model.booking;

import com.example.bbcinema.enums.FnbStatus;
import com.example.bbcinema.enums.OrderChannel;
import com.example.bbcinema.enums.PaymentStatus;
import com.example.bbcinema.model.cinema.Cinema;
import com.example.bbcinema.model.fnb.Promotion;
import com.example.bbcinema.model.management.StaffShift;
import com.example.bbcinema.model.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_code", nullable = false, unique = true, length = 50)
    private String orderCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id")
    private User staff;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_id")
    private StaffShift shift;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cinema_id", nullable = false)
    private Cinema cinema;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promotion_id")
    private Promotion promotion;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_channel", nullable = false, length = 30)
    private OrderChannel orderChannel;

    @Column(name = "ticket_subtotal", precision = 12, scale = 2, nullable = false)
    private BigDecimal ticketSubtotal = BigDecimal.ZERO;

    @Column(name = "product_subtotal", precision = 12, scale = 2, nullable = false)
    private BigDecimal productSubtotal = BigDecimal.ZERO;

    @Column(name = "discount_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "points_used", nullable = false)
    private Integer pointsUsed = 0;

    @Column(name = "points_discount_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal pointsDiscountAmount = BigDecimal.ZERO;

    @Column(name = "final_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal finalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 30)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "fnb_fulfillment_status", nullable = false, length = 30)
    private FnbStatus fnbFulfillmentStatus = FnbStatus.NONE;

    @Column(name = "served_at")
    private LocalDateTime servedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "served_by_staff_id")
    private User servedByStaff;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}