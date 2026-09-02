package com.gscrm.model;

import com.gscrm.model.enums.AuditAction;
import com.gscrm.tenant.TenantEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Filter(name = "tenantFilter", condition = "salon_id = :salonId")
@Table(name = "audit_log_entry", indexes = {
        @Index(name = "idx_audit_created_at", columnList = "created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogEntry implements TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "salon_id", nullable = false)
    private Long salonId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "entity_type", length = 64)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AuditAction action;

    @Column(name = "old_value", columnDefinition = "text")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "text")
    private String newValue;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
