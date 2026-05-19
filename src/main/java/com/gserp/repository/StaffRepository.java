package com.gserp.repository;

import com.gserp.model.Staff;
import com.gserp.model.enums.ServiceCategory;
import com.gserp.model.enums.StaffRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StaffRepository extends JpaRepository<Staff, Long> {
    List<Staff> findByActiveTrueAndRole(StaffRole role);

    /**
     * Uzmanlık alanına göre aktif personel sorgusu.
     * StaffService.getBySpecialization() içindeki in-memory filtrelemeyi ortadan kaldırır.
     */
    @Query("SELECT s FROM Staff s JOIN s.specializations spec WHERE s.active = true AND spec = :category")
    List<Staff> findActiveBySpecialization(@Param("category") ServiceCategory category);
}
