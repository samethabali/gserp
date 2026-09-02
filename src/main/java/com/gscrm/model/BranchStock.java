package com.gscrm.model;

import com.gscrm.tenant.TenantEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Filter(name = "tenantFilter", condition = "salon_id = :salonId")
@Table(name = "branch_stock", uniqueConstraints = @UniqueConstraint(
        name = "uk_branch_stock", columnNames = {"salon_id", "product_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchStock implements TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "salon_id", nullable = false)
    private Long salonId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    @Builder.Default
    private int quantity = 0;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
