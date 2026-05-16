package com.gserp.controller;

import com.gserp.dto.response.ApiResponse;
import com.gserp.model.Staff;
import com.gserp.service.StaffService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Staff>> create(@RequestBody Staff staff) {
        return ResponseEntity.ok(ApiResponse.ok("Personel eklendi", staffService.create(staff)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Staff>> update(@PathVariable Long id, @RequestBody Staff staff) {
        return ResponseEntity.ok(ApiResponse.ok("Personel güncellendi", staffService.update(id, staff)));
    }
}
