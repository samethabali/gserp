package com.gserp.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "branch_service_price", uniqueConstraints = @UniqueConstraint(
        name = "uk_branch_service_price", columnNames = {"salon_id", "service_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchServicePrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "salon_id", nullable = false)
    private Long salonId;

    @Column(name = "service_id", nullable = false)
    private Long serviceId;

    @Column(name = "price_override", precision = 12, scale = 2)
    private BigDecimal priceOverride;

    @Column(name = "duration_override")
    private Integer durationOverride;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
