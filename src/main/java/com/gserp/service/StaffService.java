package com.gserp.service;

import com.gserp.model.Staff;
import com.gserp.model.enums.StaffRole;
import com.gserp.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StaffService {

    private final StaffRepository staffRepository;

    public List<Staff> getAll() {
        return staffRepository.findAll();
    }

    public List<Staff> getActiveSpecialists() {
        return staffRepository.findByActiveTrueAndRole(StaffRole.SPECIALIST);
    }

    public Optional<Staff> getById(Long id) {
        return staffRepository.findById(id);
    }

    @Transactional
    public Staff create(Staff staff) {
        LocalDateTime now = LocalDateTime.now();
        staff.setCreatedAt(now);
        staff.setUpdatedAt(now);
        return staffRepository.save(staff);
    }

    @Transactional
    public Staff update(Long id, Staff updated) {
        Staff existing = staffRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Personel bulunamadı: " + id));
        existing.setName(updated.getName());
        existing.setPhone(updated.getPhone());
        existing.setEmail(updated.getEmail());
        existing.setRole(updated.getRole());
        existing.setColorHex(updated.getColorHex());
        existing.setActive(updated.isActive());
        existing.setUpdatedAt(LocalDateTime.now());
        return staffRepository.save(existing);
    }
}
