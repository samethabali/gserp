package com.gserp.service;

import com.gserp.model.AuditLogEntry;
import com.gserp.model.enums.AuditAction;
import com.gserp.repository.AuditLogEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogEntryRepository auditRepository;

    @Transactional
    public void log(Long userId, AuditAction action, String entityType, Long entityId,
                    String oldValue, String newValue) {
        AuditLogEntry entry = AuditLogEntry.builder()
                .userId(userId)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .oldValue(oldValue)
                .newValue(newValue)
                .createdAt(LocalDateTime.now())
                .build();
        auditRepository.save(entry);
        log.info("AUDIT: {} {} #{} by user #{}", action, entityType, entityId, userId);
    }

    /**
     * Convenience — log with system user (id=0). Until Aşama 2 lands a real
     * SecurityContext, callers without an authenticated principal use this.
     */
    public void log(AuditAction action, String entityType, Long entityId,
                    String oldValue, String newValue) {
        log(0L, action, entityType, entityId, oldValue, newValue);
    }

    @Transactional(readOnly = true)
    public List<AuditLogEntry> getRecent(int limit) {
        return auditRepository.findAllByOrderByIdDesc(PageRequest.of(0, limit));
    }
}
