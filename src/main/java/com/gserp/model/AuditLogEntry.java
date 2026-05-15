package com.gserp.model;

import com.gserp.model.enums.AuditAction;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogEntry {
    private Long id;
    private Long userId;
    private String entityType;
    private Long entityId;
    private AuditAction action;
    private String oldValue;
    private String newValue;
    private LocalDateTime createdAt;
}
