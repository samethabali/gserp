package com.gserp.repository;

import com.gserp.model.AuditLogEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditLogEntryRepository extends JpaRepository<AuditLogEntry, Long> {
    List<AuditLogEntry> findAllByOrderByIdDesc(Pageable pageable);

    void deleteByCreatedAtBefore(LocalDateTime cutoff);
}
