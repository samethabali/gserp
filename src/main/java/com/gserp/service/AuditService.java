package com.gserp.service;

import com.gserp.model.AuditLogEntry;
import com.gserp.model.enums.AuditAction;
import com.gserp.repository.AuditLogEntryRepository;
import com.gserp.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
     * Convenience — SecurityContext'ten o anki principal'in user id'sini çeker.
     * Anonim/system bağlamlarda 0L fallback'i kullanılır.
     */
    public void log(AuditAction action, String entityType, Long entityId,
                    String oldValue, String newValue) {
        log(currentUserId(), action, entityType, entityId, oldValue, newValue);
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return user.getId();
        }
        return 0L;
    }

    @Transactional(readOnly = true)
    public List<AuditLogEntry> getRecent(int limit) {
        return auditRepository.findAllByOrderByIdDesc(PageRequest.of(0, limit));
    }
}
