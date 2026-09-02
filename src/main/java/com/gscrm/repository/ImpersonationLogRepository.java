package com.gscrm.repository;

import com.gscrm.model.ImpersonationLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImpersonationLogRepository extends JpaRepository<ImpersonationLog, Long> {
}
