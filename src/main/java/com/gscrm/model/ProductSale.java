package com.gscrm.model;

import com.gscrm.tenant.TenantEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_sale", indexes = {
        @Index(name = "idx_product_sale_sold_at", columnList = "sold_at"),
        @Index(name = "idx_product_sale_product", columnList = "product_id"),
        @Index(name = "idx_product_sale_customer", columnList = "customer_id")
})
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ProductSale implements TenantEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "salon_id", nullable = false)
    private Long salonId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "total_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "appointment_id")
    private Long appointmentId;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "customer_phone")
    private String customerPhone;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "sold_at", nullable = false)
    private LocalDateTime soldAt;

    @Column(name = "staff_id")
    private Long staffId;

    @PrePersist
    void prePersist() { if (soldAt == null) soldAt = LocalDateTime.now(); }
}
