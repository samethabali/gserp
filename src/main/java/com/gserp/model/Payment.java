package com.gserp.model;

import com.gserp.model.enums.PaymentMethod;
import com.gserp.model.enums.PaymentStatus;
import com.gserp.tenant.TenantEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment", indexes = {
        @Index(name = "idx_payment_appointment",  columnList = "appointment_id"),
        @Index(name = "idx_payment_collected_at", columnList = "collected_at"),
        @Index(name = "idx_payment_customer",     columnList = "customer_phone")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment implements TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "salon_id", nullable = false)
    private Long salonId;

    @Column(name = "appointment_id", nullable = false)
    private Long appointmentId;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "customer_phone")
    private String customerPhone;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PAID;

    @Column(name = "deferred_note", columnDefinition = "text")
    private String deferredNote;

    @Column(name = "collected_at")
    private LocalDateTime collectedAt;

    @Column(name = "staff_id")
    private Long staffId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = PaymentStatus.PAID;
    }
}
