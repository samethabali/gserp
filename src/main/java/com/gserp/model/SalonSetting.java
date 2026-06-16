package com.gserp.model;

import com.gserp.tenant.TenantEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "salon_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalonSetting implements TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "salon_id", nullable = false)
    private Long salonId;

    @Column(name = "setting_key", nullable = false, length = 64)
    private String key;

    @Column(name = "setting_value")
    private String value;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
