package com.gserp.model;

import com.gserp.tenant.TenantEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationLog implements TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "salon_id", nullable = false)
    private Long salonId;

    @Column(name = "appointment_id")
    private Long appointmentId;

    @Column(nullable = false, length = 32)
    private String channel;

    @Column(name = "template_name", length = 64)
    private String templateName;

    @Column(length = 64)
    private String recipient;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;
}
