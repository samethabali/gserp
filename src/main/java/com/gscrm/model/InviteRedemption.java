package com.gscrm.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Bir davet kodunun tek bir kullanımı.
 *
 * <p>Daha önce bu bilgi {@code invite_code.redeemed_organization_id} tek kolonunda
 * tutuluyordu ve her kullanımda üzerine yazılıyordu; {@code max_uses > 1} olan bir
 * kodda yalnızca son kullanan işletme görünüyordu. Ayrı satır tutulduğu için artık
 * kullanım tarihi ve kaydı yapan admin de kayıt altında.
 */
@Entity
@Table(name = "invite_redemption")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InviteRedemption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invite_code_id", nullable = false)
    private Long inviteCodeId;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "salon_id", nullable = false)
    private Long salonId;

    @Column(name = "salon_slug", nullable = false, length = 64)
    private String salonSlug;

    @Column(name = "admin_user_id")
    private Long adminUserId;

    @Column(length = 64)
    private String ip;

    @Column(name = "redeemed_at", nullable = false)
    private LocalDateTime redeemedAt;
}
