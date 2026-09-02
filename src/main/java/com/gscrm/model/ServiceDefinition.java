package com.gscrm.model;

import com.gscrm.model.enums.ServiceCategory;
import com.gscrm.tenant.TenantEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Filter(name = "tenantFilter", condition = "salon_id = :salonId")
@Table(name = "service_definition")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceDefinition implements TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "salon_id", nullable = false)
    private Long salonId;

    @Column(nullable = false)
    private String name;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "base_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal basePrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ServiceCategory category;

    @Column(name = "requires_resource", nullable = false)
    private boolean requiresResource;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /** IDs of resources required for this service */
    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "service_required_resources",
            joinColumns = @JoinColumn(name = "service_id"))
    @Column(name = "resource_id")
    private List<Long> requiredResourceIds = new ArrayList<>();
}
