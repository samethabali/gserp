package com.gserp.repository;

import com.gserp.model.Staff;
import com.gserp.model.enums.StaffRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StaffRepository extends JpaRepository<Staff, Long> {
    List<Staff> findByActiveTrueAndRole(StaffRole role);
}
