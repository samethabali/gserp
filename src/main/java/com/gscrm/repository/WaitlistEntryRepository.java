package com.gscrm.repository;

import com.gscrm.model.WaitlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntry, Long> {
    List<WaitlistEntry> findByFulfilledFalse();

    /**
     * Eşleşen bekleme listesi kayıtlarını DB seviyesinde filtreler.
     * WaitlistService.findMatchingEntries() içindeki in-memory filtrelemeyi ortadan kaldırır.
     */
    @Query("""
           SELECT w FROM WaitlistEntry w
           WHERE w.fulfilled = false
             AND ((w.serviceId IS NOT NULL AND w.serviceId = :serviceId)
              OR  (w.preferredStaffId IS NOT NULL AND w.preferredStaffId = :staffId))
           """)
    List<WaitlistEntry> findMatchingUnfulfilled(@Param("serviceId") Long serviceId,
                                                 @Param("staffId") Long staffId);
}
