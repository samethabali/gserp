package com.gscrm.controller;

import com.gscrm.dto.response.ApiResponse;
import com.gscrm.model.BranchHoliday;
import com.gscrm.service.BranchHolidayService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/holidays")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','BRANCH_MANAGER','ORG_OWNER','PLATFORM_ADMIN')")
public class BranchHolidayController {

    private final BranchHolidayService branchHolidayService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<BranchHoliday>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(branchHolidayService.listForCurrentSalon()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BranchHoliday>> create(@RequestBody Map<String, String> body) {
        LocalDate date = LocalDate.parse(body.get("date"));
        BranchHoliday created = branchHolidayService.add(date, body.get("reason"));
        return ResponseEntity.ok(ApiResponse.ok("Tatil eklendi", created));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        branchHolidayService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Silindi", null));
    }
}
