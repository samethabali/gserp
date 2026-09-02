package com.gscrm.repository;

import com.gscrm.model.ConsentRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, Long> {

    List<ConsentRecord> findByCustomerIdAndSalonId(Long customerId, Long salonId);

    Optional<ConsentRecord> findFirstByCustomerIdAndSalonIdAndConsentTypeAndRevokedAtIsNull(
            Long customerId, Long salonId, String consentType);
}
