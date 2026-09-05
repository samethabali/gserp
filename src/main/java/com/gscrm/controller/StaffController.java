package com.gscrm.controller;

import com.gscrm.dto.response.ApiResponse;
import com.gscrm.dto.response.StaffAccountResponse;
import com.gscrm.dto.response.StaffCreateResponse;
import com.gscrm.model.Staff;
import com.gscrm.model.WorkingHours;
import com.gscrm.model.enums.ServiceCategory;
import com.gscrm.service.StaffAccountService;
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

    private static final String MGMT = "hasAnyRole('ADMIN','BRANCH_MANAGER','ORG_OWNER','PLATFORM_ADMIN')";

    private final StaffService staffService;
    private final StaffAccountService staffAccountService;

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

    /**
     * Personeli ekler; {@code createAccount=false} verilmedikçe giriş hesabını da açar.
     *
     * <p>Geçici parola yalnızca bu yanıtta döner — hash'lenerek saklandığı için
     * sonradan hiçbir uçtan okunamaz.
     */
    @PostMapping
    @PreAuthorize(MGMT)
    public ResponseEntity<ApiResponse<StaffCreateResponse>> create(
            @RequestBody Staff staff,
            @RequestParam(name = "createAccount", defaultValue = "true") boolean createAccount) {
        return ResponseEntity.ok(ApiResponse.ok("Personel eklendi",
                staffService.createWithAccount(staff, createAccount)));
    }

    /**
     * Personel kartlarında hesap durumunu göstermek için.
     *
     * <p>Yol düzeyinde {@code GET /api/staff/**} tüm personele açık; kullanıcı adı
     * listesi yönetime kalmalı, bu yüzden metot ayrıca kısıtlanır.
     */
    @GetMapping("/accounts")
    @PreAuthorize(MGMT)
    public ResponseEntity<ApiResponse<List<StaffAccountResponse>>> accounts() {
        return ResponseEntity.ok(ApiResponse.ok(staffAccountService.listAccounts()));
    }

    /** Hesabı olmayan (bu özellikten önce eklenmiş) personele sonradan hesap açar. */
    @PostMapping("/{id}/account")
    @PreAuthorize(MGMT)
    public ResponseEntity<ApiResponse<StaffAccountResponse>> createAccount(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Giriş hesabı oluşturuldu",
                staffAccountService.provision(id)));
    }

    /** Yeni geçici parola üretir; personel ilk girişte tekrar değiştirmek zorunda kalır. */
    @PostMapping("/{id}/account/reset-password")
    @PreAuthorize(MGMT)
    public ResponseEntity<ApiResponse<StaffAccountResponse>> resetAccountPassword(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Geçici parola oluşturuldu",
                staffAccountService.resetPassword(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize(MGMT)
    public ResponseEntity<ApiResponse<Staff>> update(@PathVariable Long id, @RequestBody Staff staff) {
        return ResponseEntity.ok(ApiResponse.ok("Personel güncellendi", staffService.update(id, staff)));
    }

    @PutMapping("/{id}/specializations")
    @PreAuthorize(MGMT)
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
    @PreAuthorize(MGMT)
    public ResponseEntity<ApiResponse<List<WorkingHours>>> saveWorkingHours(
            @PathVariable Long id,
            @RequestBody List<WorkingHours> hours) {
        return ResponseEntity.ok(ApiResponse.ok("Çalışma saatleri kaydedildi",
                staffService.saveWorkingHours(id, hours)));
    }
}
