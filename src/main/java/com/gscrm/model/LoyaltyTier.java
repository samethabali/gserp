package com.gscrm.model;

import com.gscrm.tenant.TenantEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Filter(name = "tenantFilter", condition = "salon_id = :salonId")
@Table(name = "loyalty_tier")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoyaltyTier implements TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "salon_id", nullable = false)
    private Long salonId;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(name = "min_completed", nullable = false)
    private int minCompleted;

    @Column(name = "discount_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPercentage;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
