package com.gserp.repository;

import com.gserp.model.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {
    void deleteBySentAtBefore(LocalDateTime cutoff);
}
