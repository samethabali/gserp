package com.gscrm.model;

import com.gscrm.tenant.TenantEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Filter(name = "tenantFilter", condition = "salon_id = :salonId")
@Table(name = "salon_whatsapp_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalonWhatsAppConfig implements TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "salon_id", nullable = false, unique = true)
    private Long salonId;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = false;

    @Column(name = "token_enc", columnDefinition = "text")
    private String tokenEnc;

    @Column(name = "phone_number_id", length = 64)
    private String phoneNumberId;

    @Column(name = "business_account_id", length = 64)
    private String businessAccountId;

    @Column(name = "salon_phone_e164", length = 32)
    private String salonPhoneE164;

    @Column(name = "webhook_verify_token", length = 128)
    private String webhookVerifyToken;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
