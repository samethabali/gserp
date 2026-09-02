package com.gscrm.controller;

import com.gscrm.dto.response.ApiResponse;
import com.gscrm.model.Staff;
import com.gscrm.model.WorkingHours;
import com.gscrm.model.enums.ServiceCategory;
import com.gscrm.service.StaffService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/staff")
@RequiredArgsConstructor
public class StaffController {

    private final StaffService staffService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Staff>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(staffService.getAll()));
    }

    @GetMapping("/specialists")
    public ResponseEntity<ApiResponse<List<Staff>>> getSpecialists() {
        return ResponseEntity.ok(ApiResponse.ok(staffService.getActiveSpecialists()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Staff>> getById(@PathVariable Long id) {
        return staffService.getById(id)
                .map(s -> ResponseEntity.ok(ApiResponse.ok(s)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','BRANCH_MANAGER','ORG_OWNER','PLATFORM_ADMIN')")
    public ResponseEntity<ApiResponse<Staff>> create(@RequestBody Staff staff) {
        return ResponseEntity.ok(ApiResponse.ok("Personel eklendi", staffService.create(staff)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','BRANCH_MANAGER','ORG_OWNER','PLATFORM_ADMIN')")
    public ResponseEntity<ApiResponse<Staff>> update(@PathVariable Long id, @RequestBody Staff staff) {
        return ResponseEntity.ok(ApiResponse.ok("Personel güncellendi", staffService.update(id, staff)));
    }

    @PutMapping("/{id}/specializations")
    @PreAuthorize("hasAnyRole('ADMIN','BRANCH_MANAGER','ORG_OWNER','PLATFORM_ADMIN')")
    public ResponseEntity<ApiResponse<Staff>> updateSpecializations(
            @PathVariable Long id,
            @RequestBody Set<ServiceCategory> categories) {
        return ResponseEntity.ok(ApiResponse.ok("Uzmanlıklar güncellendi",
                staffService.updateSpecializations(id, categories)));
    }

    @GetMapping("/by-specialization")
    public ResponseEntity<ApiResponse<List<Staff>>> getBySpecialization(
            @RequestParam ServiceCategory category) {
        return ResponseEntity.ok(ApiResponse.ok(staffService.getBySpecialization(category)));
    }

    @GetMapping("/{id}/working-hours")
    public ResponseEntity<ApiResponse<List<WorkingHours>>> getWorkingHours(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(staffService.getWorkingHours(id)));
    }

    @PutMapping("/{id}/working-hours")
    @PreAuthorize("hasAnyRole('ADMIN','BRANCH_MANAGER','ORG_OWNER','PLATFORM_ADMIN')")
    public ResponseEntity<ApiResponse<List<WorkingHours>>> saveWorkingHours(
            @PathVariable Long id,
            @RequestBody List<WorkingHours> hours) {
        return ResponseEntity.ok(ApiResponse.ok("Çalışma saatleri kaydedildi",
                staffService.saveWorkingHours(id, hours)));
    }
}
