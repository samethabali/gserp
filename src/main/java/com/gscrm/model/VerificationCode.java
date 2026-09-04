package com.gscrm.model;

import com.gscrm.tenant.TenantEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;

import java.time.LocalDateTime;

/**
 * Telefon doğrulama kodu ve doğrulanmış durumun tek kullanımlık kulpu.
 *
 * <p>Doğrulanmış durum imzalı bir token yerine bu satırda taşınıyor. Sebep: gereksinim
 * <b>tek kullanımlık</b> olması ve imzalı token bunu ifade edemiyor — süresi dolana
 * kadar tekrar oynatılabilir, iptali sunucu durumu gerektirir; yani JWT yolu "kripto
 * + veritabanı satırı" olarak biterdi. Satır zaten kod, süre ve deneme sayacı için
 * gerekli. Üstelik salon bir randevuya itiraz ettiğinde doğrulama sorgulanabilir bir
 * kayıttır.
 */
@Entity
@Filter(name = "tenantFilter", condition = "salon_id = :salonId")
@Table(name = "verification_code")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationCode implements TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "salon_id", nullable = false)
    private Long salonId;

    @Column(name = "phone_normalized", nullable = false, length = 32)
    private String phoneNormalized;

    @Column(name = "code_hash", nullable = false, length = 72)
    private String codeHash;

    @Column(nullable = false, length = 32)
    @Builder.Default
    private String purpose = "BOOKING";

    @Column(nullable = false)
    @Builder.Default
    private int attempts = 0;

    @Column(name = "max_attempts", nullable = false)
    @Builder.Default
    private int maxAttempts = 5;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    @Column(name = "verification_token", length = 64)
    private String verificationToken;

    @Column(name = "request_ip", length = 64)
    private String requestIp;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    public boolean isExpired(LocalDateTime now) {
        return expiresAt == null || now.isAfter(expiresAt);
    }

    public boolean isAttemptsExhausted() {
        return attempts >= maxAttempts;
    }
}
