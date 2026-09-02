package com.gscrm.service;

import com.gscrm.repository.AuditLogEntryRepository;
import com.gscrm.repository.NotificationLogRepository;
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

    private final AuditLogEntryRepository auditLogEntryRepository;
    private final NotificationLogRepository notificationLogRepository;

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeOldLogs() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS);
        auditLogEntryRepository.deleteByCreatedAtBefore(cutoff);
        notificationLogRepository.deleteBySentAtBefore(cutoff);
        log.info("Retention purge completed before {}", cutoff);
    }
}
