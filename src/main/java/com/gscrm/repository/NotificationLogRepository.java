package com.gscrm.repository;

import com.gscrm.model.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {
    void deleteBySentAtBefore(LocalDateTime cutoff);
}
