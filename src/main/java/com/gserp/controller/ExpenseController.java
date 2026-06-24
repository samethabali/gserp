package com.gserp.controller;

import com.gserp.dto.request.ExpenseCreateRequest;
import com.gserp.dto.response.ApiResponse;
import com.gserp.model.Expense;
import com.gserp.service.ExpenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','BRANCH_MANAGER','ORG_OWNER','PLATFORM_ADMIN','RECEPTIONIST')")
public class ExpenseController {

    private final ExpenseService expenseService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Expense>>> getByRange(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate f = from != null ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate t = to   != null ? to   : LocalDate.now();
        return ResponseEntity.ok(ApiResponse.ok(expenseService.getByDateRange(f, t)));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String, BigDecimal>>> getSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate f = from != null ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate t = to   != null ? to   : LocalDate.now();
        return ResponseEntity.ok(ApiResponse.ok(expenseService.getMonthlySummary(f, t)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Expense>> create(@Valid @RequestBody ExpenseCreateRequest request) {
        Expense expense = Expense.builder()
                .description(request.getDescription())
                .amount(request.getAmount())
                .expenseDate(request.getExpenseDate())
                .category(request.getCategory())
                .build();
        return ResponseEntity.ok(ApiResponse.ok("Gider eklendi", expenseService.create(expense)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Expense>> update(
            @PathVariable Long id, @Valid @RequestBody ExpenseCreateRequest request) {
        Expense expense = Expense.builder()
                .description(request.getDescription())
                .amount(request.getAmount())
                .expenseDate(request.getExpenseDate())
                .category(request.getCategory())
                .build();
        return ResponseEntity.ok(ApiResponse.ok("Gider güncellendi", expenseService.update(id, expense)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        expenseService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Gider silindi", null));
    }
}
