package com.gscrm.repository;

import com.gscrm.model.BranchHoliday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface BranchHolidayRepository extends JpaRepository<BranchHoliday, Long> {

    List<BranchHoliday> findBySalonIdOrderByHolidayDateAsc(Long salonId);

    boolean existsBySalonIdAndHolidayDate(Long salonId, LocalDate holidayDate);
}
