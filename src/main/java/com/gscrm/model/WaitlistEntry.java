package com.gscrm.model;

import com.gscrm.tenant.TenantEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "waitlist_entry")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaitlistEntry implements TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "salon_id", nullable = false)
    private Long salonId;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "customer_phone")
    private String customerPhone;

    @Column(name = "service_id")
    private Long serviceId;

    @Column(name = "preferred_staff_id")
    private Long preferredStaffId;

    @Column(name = "preferred_date")
    private LocalDate preferredDate;

    @Column(name = "preferred_time")
    private LocalTime preferredTime;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(nullable = false)
    private boolean fulfilled;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
