package com.gscrm.model;

import com.gscrm.model.enums.ServiceCategory;
import com.gscrm.model.enums.StaffRole;
import com.gscrm.tenant.TenantEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Filter(name = "tenantFilter", condition = "salon_id = :salonId")
@Table(name = "staff")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Staff implements TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "salon_id", nullable = false)
    private Long salonId;

    @Column(nullable = false)
    private String name;

    private String phone;
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private StaffRole role;

    @Column(length = 16)
    private String colorHex;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "staff_specialization", joinColumns = @JoinColumn(name = "staff_id"))
    @Column(name = "service_category")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Set<ServiceCategory> specializations = new HashSet<>();
}
