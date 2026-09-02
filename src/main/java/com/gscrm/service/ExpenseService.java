package com.gscrm.service;

import com.gscrm.model.Expense;
import com.gscrm.model.enums.ExpenseCategory;
import com.gscrm.repository.ExpenseRepository;
import com.gscrm.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExpenseService {

    private final ExpenseRepository expenseRepository;

    public List<Expense> getByDateRange(LocalDate from, LocalDate to) {
        return expenseRepository.findByExpenseDateBetweenOrderByExpenseDateDesc(from, to);
    }

    public Map<String, BigDecimal> getMonthlySummary(LocalDate from, LocalDate to) {
        Map<String, BigDecimal> summary = new LinkedHashMap<>();
        summary.put("TOTAL", expenseRepository.sumByDateRange(from, to));
        for (ExpenseCategory cat : ExpenseCategory.values()) {
            summary.put(cat.name(), expenseRepository.sumByDateRangeAndCategory(from, to, cat));
        }
        return summary;
    }

    @Transactional
    public Expense create(Expense expense) {
        // Gider, oluşturulduğu şubeye bağlanmalıdır. V15 salon_id'yi NOT NULL yaptı
        // ama yazma yolu güncellenmediği için bu alan boş kalıyordu.
        if (expense.getSalonId() == null) {
            expense.setSalonId(TenantContext.requireSalonId());
        }
        return expenseRepository.save(expense);
    }

    @Transactional
    public Expense update(Long id, Expense updated) {
        Expense existing = expenseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Gider bulunamadı: " + id));
        existing.setDescription(updated.getDescription());
        existing.setAmount(updated.getAmount());
        existing.setCategory(updated.getCategory());
        existing.setExpenseDate(updated.getExpenseDate());
        existing.setNotes(updated.getNotes());
        return expenseRepository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        expenseRepository.deleteById(id);
    }
}
