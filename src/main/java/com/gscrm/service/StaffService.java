package com.gscrm.service;

import com.gscrm.model.Staff;
import com.gscrm.model.WorkingHours;
import com.gscrm.model.enums.ServiceCategory;
import com.gscrm.model.enums.StaffRole;
import com.gscrm.repository.StaffRepository;
import com.gscrm.repository.WorkingHoursRepository;
import com.gscrm.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StaffService {

    private final StaffRepository staffRepository;
    private final WorkingHoursRepository workingHoursRepository;

    public List<Staff> getAll() {
        return staffRepository.findAll();
    }

    public List<Staff> getActiveSpecialists() {
        return staffRepository.findBySalonIdAndActiveTrueAndRole(TenantContext.requireSalonId(), StaffRole.SPECIALIST);
    }

    public Optional<Staff> getById(Long id) {
        return staffRepository.findById(id);
    }

    @Transactional
    public Staff create(Staff staff) {
        // Uçlar ham entity kabul ettiği için istemci gövdeye salonId koyabilir;
        // tenant sunucu tarafında zorlanır (mass assignment koruması).
        staff.setSalonId(TenantContext.requireSalonId());
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

    @Transactional
    @SuppressWarnings("null")
    public Staff updateSpecializations(Long id, Set<ServiceCategory> categories) {
        Staff staff = staffRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Personel bulunamadı: " + id));
        staff.getSpecializations().clear();
        if (categories != null) staff.getSpecializations().addAll(categories);
        staff.setUpdatedAt(LocalDateTime.now());
        return staffRepository.save(staff);
    }

    public List<Staff> getBySpecialization(ServiceCategory category) {
        return staffRepository.findActiveBySalonIdAndSpecialization(TenantContext.requireSalonId(), category);
    }

    public List<WorkingHours> getWorkingHours(Long staffId) {
        return workingHoursRepository.findByStaffId(staffId);
    }

    @Transactional
    public List<WorkingHours> saveWorkingHours(Long staffId, List<WorkingHours> incoming) {
        // Mevcut kayıtları sil, yenileri kaydet
        List<WorkingHours> existing = workingHoursRepository.findByStaffId(staffId);
        workingHoursRepository.deleteAll(existing);

        List<WorkingHours> toSave = new ArrayList<>();
        for (DayOfWeek dow : DayOfWeek.values()) {
            final DayOfWeek d = dow;
            WorkingHours wh = incoming.stream()
                    .filter(w -> w.getDayOfWeek() == d)
                    .findFirst()
                    .orElse(WorkingHours.builder().dayOfWeek(d).dayOff(true).build());
            wh.setId(null);
            wh.setStaffId(staffId);
            toSave.add(wh);
        }
        return workingHoursRepository.saveAll(toSave);
    }
}
