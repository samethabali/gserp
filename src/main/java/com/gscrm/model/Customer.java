package com.gscrm.model;

import com.gscrm.tenant.TenantEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Filter(name = "tenantFilter", condition = "salon_id = :salonId")
@Table(name = "customer")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Customer implements TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "salon_id", nullable = false)
    private Long salonId;

    @Column(name = "home_salon_id")
    private Long homeSalonId;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    private String phone;

    /**
     * {@code phone}'un kanonik E.164 hâli — müşteri eşleştirmesinin anahtarı.
     * Doğrudan yazılmaz; {@link #syncNormalizedPhone()} her kayıtta üretir.
     */
    @Column(name = "phone_normalized", length = 32)
    private String phoneNormalized;

    private String email;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "consent_at")
    private LocalDateTime consentAt;

    @Column(precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    /**
     * Normalize telefonu her yazımda yeniden üretir.
     *
     * <p>Entity düzeyinde durmasının sebebi: müşteri kaydı yazan yol tek değil
     * (panel CRUD, portal kaydı, online randevu, seeder). Callback olunca hiçbir
     * çağrı yeri normalizasyonu unutamaz.
     */
    @PrePersist
    @PreUpdate
    private void syncNormalizedPhone() {
        this.phoneNormalized = com.gscrm.util.PhoneNormalizer.normalizeOrNull(this.phone);
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }
}
