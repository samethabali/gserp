package com.gscrm.repository;

import com.gscrm.model.SmsLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SmsLogRepository extends JpaRepository<SmsLog, Long> {
}
