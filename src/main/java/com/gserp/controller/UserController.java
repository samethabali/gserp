package com.gserp.controller;

import com.gserp.dto.request.UserCreateRequest;
import com.gserp.dto.response.ApiResponse;
import com.gserp.model.User;
import com.gserp.service.UserService;
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
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(userService.listStaffUsers()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<User>> create(@Valid @RequestBody UserCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Kullanıcı oluşturuldu", userService.create(request)));
    }

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @PathVariable Long id, @RequestBody Map<String, String> body) {
        String password = body.get("password");
        if (password == null || password.length() < 6) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Parola en az 6 karakter olmalı"));
        }
        userService.resetPassword(id, password);
        return ResponseEntity.ok(ApiResponse.ok("Parola sıfırlandı", null));
    }

    @PatchMapping("/{id}/enabled")
    public ResponseEntity<ApiResponse<User>> setEnabled(
            @PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        return ResponseEntity.ok(ApiResponse.ok("Durum güncellendi", userService.setEnabled(id, enabled)));
    }
}
