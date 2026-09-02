package com.gscrm.model;

import com.gscrm.model.enums.UserRole;
import com.gscrm.tenant.TenantEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User implements TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "salon_id", nullable = false)
    private Long salonId;

    @Column(name = "organization_id")
    private Long organizationId;

    @Column(nullable = false, length = 64)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UserRole role;

    @Column(name = "staff_id")
    private Long staffId;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    /**
     * Token iptal sayacı. Parola değişimi ve hesabın devre dışı bırakılması bu
     * değeri artırır; artıştan önce üretilmiş erişim/yenileme token'ları geçersiz olur.
     */
    @Column(name = "token_version", nullable = false)
    @Builder.Default
    private int tokenVersion = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
