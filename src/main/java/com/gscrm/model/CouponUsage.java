package com.gscrm.model;

import com.gscrm.tenant.TenantEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Filter(name = "tenantFilter", condition = "salon_id = :salonId")
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
