package com.gscrm.repository;

import com.gscrm.model.VerificationCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface VerificationCodeRepository extends JpaRepository<VerificationCode, Long> {

    /** En son üretilen kod — doğrulama hep bunun üzerinden yapılır. */
    Optional<VerificationCode> findFirstBySalonIdAndPhoneNormalizedAndPurposeOrderByCreatedAtDesc(
            Long salonId, String phoneNormalized, String purpose);

    /**
     * Numara başına kod üretim kotası.
     *
     * <p>Veritabanında sayılması şart: {@code RateLimitFilter} IP'ye göre çalışıyor,
     * IP döndüren bir saldırgan tek bir numarayı yine de dövebilirdi.
     */
    long countBySalonIdAndPhoneNormalizedAndCreatedAtAfter(
            Long salonId, String phoneNormalized, LocalDateTime after);

    Optional<VerificationCode> findByVerificationToken(String verificationToken);

    List<VerificationCode> findByExpiresAtBefore(LocalDateTime cutoff);

    @Modifying
    @Query("delete from VerificationCode v where v.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);
}
