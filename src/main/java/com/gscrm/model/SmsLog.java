package com.gscrm.model;

import com.gscrm.tenant.TenantEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;

import java.time.LocalDateTime;

/** Gönderilen (ya da gönderilmeye çalışılan) her mesajın kaydı — sağlayıcı bağımsız. */
@Entity
@Filter(name = "tenantFilter", condition = "salon_id = :salonId")
@Table(name = "sms_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmsLog implements TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "salon_id", nullable = false)
    private Long salonId;

    @Column(nullable = false, length = 32)
    @Builder.Default
    private String channel = "SMS";

    @Column(name = "template_name", length = 64)
    private String templateName;

    @Column(length = 32)
    private String recipient;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(length = 32)
    private String provider;

    @Column(name = "provider_ref", length = 128)
    private String providerRef;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;
}
