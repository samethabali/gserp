package com.gscrm.service;

import com.gscrm.model.BranchHoliday;
import com.gscrm.repository.BranchHolidayRepository;
import com.gscrm.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BranchHolidayService {

    private final BranchHolidayRepository branchHolidayRepository;

    public List<BranchHoliday> listForCurrentSalon() {
        return branchHolidayRepository.findBySalonIdOrderByHolidayDateAsc(TenantContext.requireSalonId());
    }

    public boolean isHoliday(Long salonId, LocalDate date) {
        return branchHolidayRepository.existsBySalonIdAndHolidayDate(salonId, date);
    }

    @Transactional
    public BranchHoliday add(LocalDate date, String reason) {
        Long salonId = TenantContext.requireSalonId();
        if (branchHolidayRepository.existsBySalonIdAndHolidayDate(salonId, date)) {
            throw new IllegalArgumentException("Bu tarih zaten tatil olarak işaretli");
        }
        return branchHolidayRepository.save(BranchHoliday.builder()
                .salonId(salonId)
                .holidayDate(date)
                .reason(reason != null ? reason.trim() : "")
                .build());
    }

    @Transactional
    public void delete(Long id) {
        Long salonId = TenantContext.requireSalonId();
        BranchHoliday row = branchHolidayRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tatil kaydı bulunamadı"));
        if (!salonId.equals(row.getSalonId())) {
            throw new IllegalArgumentException("Bu kayda erişim yok");
        }
        branchHolidayRepository.delete(row);
    }
}
