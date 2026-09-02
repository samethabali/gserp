package com.gscrm.model;

import com.gscrm.model.enums.AppointmentStatus;
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
@Table(name = "appointment", indexes = {
        @Index(name = "idx_appointment_staff_start", columnList = "staff_id,start_time"),
        @Index(name = "idx_appointment_start", columnList = "start_time")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Appointment implements TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "salon_id", nullable = false)
    private Long salonId;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "customer_phone")
    private String customerPhone;

    @Column(name = "staff_id", nullable = false)
    private Long staffId;

    @Column(name = "service_id", nullable = false)
    private Long serviceId;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AppointmentStatus status;

    @Column(name = "base_price", precision = 12, scale = 2)
    private BigDecimal basePrice;

    @Column(precision = 12, scale = 2)
    private BigDecimal adjustment;

    @Column(name = "adjustment_note")
    private String adjustmentNote;

    @Column(name = "final_price", precision = 12, scale = 2)
    private BigDecimal finalPrice;

    @Column(name = "internal_note", columnDefinition = "text")
    private String internalNote;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @Column(name = "session_group_id")
    private String sessionGroupId;

    @Column(name = "session_number")
    private Integer sessionNumber;

    @Column(name = "total_sessions")
    private Integer totalSessions;

    @Version
    private int version;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** IDs of resources locked for this appointment */
    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "appointment_resources",
            joinColumns = @JoinColumn(name = "appointment_id"),
            indexes = @Index(name = "idx_appt_resources_resource", columnList = "resource_id"))
    @Column(name = "resource_id")
    private List<Long> resourceIds = new ArrayList<>();

    /** Quick flags/sticky notes */
    @Builder.Default
    @OneToMany(mappedBy = "appointment", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.EAGER)
    private List<AppointmentFlag> flags = new ArrayList<>();
}
