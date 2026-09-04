package com.gscrm.repository;

import com.gscrm.model.ImpersonationLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ImpersonationLogRepository extends JpaRepository<ImpersonationLog, Long> {

    /** Kapanmamış oturum: {@code ended_at} hiç yazılmadığı için hepsi açık görünüyordu. */
    Optional<ImpersonationLog> findFirstByPlatformUserIdAndEndedAtIsNullOrderByStartedAtDesc(Long platformUserId);

    List<ImpersonationLog> findBySalonIdOrderByStartedAtDesc(Long salonId, Pageable pageable);
}
