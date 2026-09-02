package com.gscrm.model;

import com.gscrm.tenant.TenantEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Filter(name = "tenantFilter", condition = "salon_id = :salonId")
@Table(name = "consent_record")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsentRecord implements TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "salon_id", nullable = false)
    private Long salonId;

    @Column(name = "consent_type", nullable = false, length = 32)
    private String consentType;

    @Column(nullable = false, length = 16)
    @Builder.Default
    private String version = "1.0";

    @Column(name = "granted_at", nullable = false)
    private LocalDateTime grantedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;
}
