package com.gserp.service;

import com.gserp.model.Staff;
import com.gserp.store.MockDataStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StaffService {

    private final MockDataStore store;

    public List<Staff> getAll() {
        return store.findAllStaff();
    }

    public List<Staff> getActiveSpecialists() {
        return store.findActiveSpecialists();
    }

    public Optional<Staff> getById(Long id) {
        return store.findStaffById(id);
    }

    public Staff create(Staff staff) {
        return store.addStaff(staff);
    }

    public Staff update(Long id, Staff updated) {
        Staff existing = store.findStaffById(id)
                .orElseThrow(() -> new IllegalArgumentException("Personel bulunamadı: " + id));
        existing.setName(updated.getName());
        existing.setPhone(updated.getPhone());
        existing.setEmail(updated.getEmail());
        existing.setRole(updated.getRole());
        existing.setColorHex(updated.getColorHex());
        existing.setActive(updated.isActive());
        return store.updateStaff(existing);
    }
}
