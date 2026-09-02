package com.gscrm.repository;

import com.gscrm.model.InviteCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InviteCodeRepository extends JpaRepository<InviteCode, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM InviteCode i WHERE i.code = :code")
    Optional<InviteCode> findByCodeForUpdate(@Param("code") String code);

    List<InviteCode> findAllByOrderByCreatedAtDesc();

    boolean existsByCode(String code);
}
