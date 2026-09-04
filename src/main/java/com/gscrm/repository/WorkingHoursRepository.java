package com.gscrm.repository;

import com.gscrm.model.WorkingHours;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkingHoursRepository extends JpaRepository<WorkingHours, Long> {
    List<WorkingHours> findByStaffId(Long staffId);

    /** Salon kapsamı imzada açık — Hibernate tenant filtresine bel bağlamaz. */
    List<WorkingHours> findByStaffIdAndSalonId(Long staffId, Long salonId);
}
