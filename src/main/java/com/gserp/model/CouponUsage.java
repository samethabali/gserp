package com.gserp.model;

import com.gserp.tenant.TenantEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "coupon_usage")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponUsage implements TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "salon_id", nullable = false)
    private Long salonId;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "appointment_id")
    private Long appointmentId;

    @Column(name = "used_at", nullable = false)
    private LocalDateTime usedAt;
}
