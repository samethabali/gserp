package com.gscrm.model;

import com.gscrm.model.enums.InviteKind;
import com.gscrm.model.enums.OrganizationType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "invite_code")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InviteCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InviteKind kind;

    @Column(name = "max_uses", nullable = false)
    @Builder.Default
    private int maxUses = 1;

    @Column(name = "used_count", nullable = false)
    @Builder.Default
    private int usedCount = 0;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "plan_code", nullable = false, length = 32)
    @Builder.Default
    private String planCode = "SOLO";

    @Enumerated(EnumType.STRING)
    @Column(name = "organization_type", nullable = false, length = 16)
    @Builder.Default
    private OrganizationType organizationType = OrganizationType.STANDALONE;

    @Column(length = 255)
    private String note;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "redeemed_organization_id")
    private Long redeemedOrganizationId;
}
