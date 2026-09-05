package com.gscrm.controller;

import com.gscrm.dto.request.UserCreateRequest;
import com.gscrm.dto.response.ApiResponse;
import com.gscrm.dto.response.UserAccountResponse;
import com.gscrm.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','BRANCH_MANAGER','ORG_OWNER','PLATFORM_ADMIN')")
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserAccountResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(userService.listStaffUsers()));
    }

    /** Formun rol listesi; sunucudaki atama kuralıyla aynı kaynaktan gelir. */
    @GetMapping("/assignable-roles")
    public ResponseEntity<ApiResponse<List<String>>> assignableRoles() {
        return ResponseEntity.ok(ApiResponse.ok(userService.assignableRoles()));
    }

    /**
     * Parola alanı boş bırakılabilir; o durumda sunucu geçici parola üretir ve
     * yanıtta bir kez döner ({@code temporaryPassword}).
     */
    @PostMapping
    public ResponseEntity<ApiResponse<UserAccountResponse>> create(@Valid @RequestBody UserCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Kullanıcı oluşturuldu", userService.create(request)));
    }

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<ApiResponse<UserAccountResponse>> resetPassword(
            @PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String password = body != null ? body.get("password") : null;
        return ResponseEntity.ok(ApiResponse.ok("Parola sıfırlandı", userService.resetPassword(id, password)));
    }

    @PatchMapping("/{id}/enabled")
    public ResponseEntity<ApiResponse<UserAccountResponse>> setEnabled(
            @PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        return ResponseEntity.ok(ApiResponse.ok("Durum güncellendi", userService.setEnabled(id, enabled)));
    }
}
