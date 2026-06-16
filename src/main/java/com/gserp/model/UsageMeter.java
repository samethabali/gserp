package com.gserp.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "usage_meter", uniqueConstraints = @UniqueConstraint(
        name = "uk_usage_meter", columnNames = {"organization_id", "salon_id", "metric", "period"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageMeter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "salon_id")
    private Long salonId;

    @Column(nullable = false, length = 32)
    private String metric;

    @Column(nullable = false, length = 7)
    private String period;

    @Column(nullable = false)
    @Builder.Default
    private int count = 0;
}
