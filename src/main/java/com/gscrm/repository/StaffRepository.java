package com.gscrm.repository;

import com.gscrm.model.Staff;
import com.gscrm.model.enums.ServiceCategory;
import com.gscrm.model.enums.StaffRole;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StaffRepository extends JpaRepository<Staff, Long> {

    Optional<Staff> findByIdAndSalonId(Long id, Long salonId);

    /**
     * Uzman kaydını satır kilidiyle yükler — randevu yazan akışlar için.
     *
     * <p>Müsaitlik kontrolü ile kaydın yazılması arasında kilit yoktu: eşzamanlı iki
     * istek kontrolü birlikte geçip aynı slota yazabiliyordu. Uzman satırı işlem
     * boyunca kilitlenince aynı uzmana gelen istekler sıraya giriyor ve ikincisi
     * birincinin commit'ini gördüğü için normal "müsait değil" yanıtını alıyor.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Staff s WHERE s.id = :id AND s.salonId = :salonId")
    Optional<Staff> lockByIdAndSalonId(@Param("id") Long id, @Param("salonId") Long salonId);

    List<Staff> findBySalonIdAndActiveTrue(Long salonId);

    List<Staff> findBySalonIdAndActiveTrueAndRole(Long salonId, StaffRole role);

    @Query("SELECT s FROM Staff s JOIN s.specializations spec WHERE s.salonId = :salonId AND s.active = true AND spec = :category")
    List<Staff> findActiveBySalonIdAndSpecialization(@Param("salonId") Long salonId, @Param("category") ServiceCategory category);
}
