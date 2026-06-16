package com.gserp.repository;

import com.gserp.model.Staff;
import com.gserp.model.enums.ServiceCategory;
import com.gserp.model.enums.StaffRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StaffRepository extends JpaRepository<Staff, Long> {

    Optional<Staff> findByIdAndSalonId(Long id, Long salonId);

    List<Staff> findBySalonIdAndActiveTrue(Long salonId);

    List<Staff> findBySalonIdAndActiveTrueAndRole(Long salonId, StaffRole role);

    @Query("SELECT s FROM Staff s JOIN s.specializations spec WHERE s.salonId = :salonId AND s.active = true AND spec = :category")
    List<Staff> findActiveBySalonIdAndSpecialization(@Param("salonId") Long salonId, @Param("category") ServiceCategory category);
}
