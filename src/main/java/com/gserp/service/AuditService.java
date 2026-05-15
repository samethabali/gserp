package com.gserp.service;

import com.gserp.model.AuditLogEntry;
import com.gserp.model.enums.AuditAction;
import com.gserp.store.MockDataStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final MockDataStore store;

    /**
     * Log any data change.
     * In production, oldValue/newValue would be JSON serialized.
     */
    public void log(Long userId, AuditAction action, String entityType, Long entityId,
                    String oldValue, String newValue) {
        AuditLogEntry entry = AuditLogEntry.builder()
                .userId(userId)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .oldValue(oldValue)
                .newValue(newValue)
                .build();
        store.addAuditLog(entry);
        log.info("AUDIT: {} {} {} #{} by user #{}", action, entityType, entityId,
                entityId, userId);
    }

    /**
     * Convenience — log with system user (id=0).
     */
    public void log(AuditAction action, String entityType, Long entityId,
                    String oldValue, String newValue) {
        log(0L, action, entityType, entityId, oldValue, newValue);
    }
}
