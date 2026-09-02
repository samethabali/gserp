package com.gscrm.model;

import com.gscrm.tenant.TenantEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Filter(name = "tenantFilter", condition = "salon_id = :salonId")
@Table(name = "onboarding_state")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnboardingState implements TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "salon_id", nullable = false, unique = true)
    private Long salonId;

    @Column(name = "current_step", nullable = false, length = 32)
    @Builder.Default
    private String currentStep = "SALON_INFO";

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
