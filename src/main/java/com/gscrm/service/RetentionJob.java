package com.gscrm.service;

import com.gscrm.repository.ActivityEventRepository;
import com.gscrm.repository.AuditLogEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RetentionJob {

    private static final int RETENTION_DAYS = 90;

    /**
     * İşlem kütüğü saklama süresi.
     *
     * <p>{@code activity_event} temizliğe hiç dahil değildi ve sınırsız büyüyordu.
     * 24 ay, KVKK kapsamında saklanan işlem kayıtları için belirlenen süre —
     * {@code docs/saas/dpa.md} ile aynı değeri taşır.
     */
    private static final int ACTIVITY_RETENTION_MONTHS = 24;

    private final AuditLogEntryRepository auditLogEntryRepository;
    private final ActivityEventRepository activityEventRepository;

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeOldLogs() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS);
        auditLogEntryRepository.deleteByCreatedAtBefore(cutoff);

        LocalDateTime activityCutoff = LocalDateTime.now().minusMonths(ACTIVITY_RETENTION_MONTHS);
        int removed = activityEventRepository.deleteByCreatedAtBefore(activityCutoff);
        log.info("Retention purge completed before {} (activity_event: {} satır, {} öncesi)",
                cutoff, removed, activityCutoff);
    }
}
