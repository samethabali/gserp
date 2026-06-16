package com.gserp.controller;

import com.gserp.dto.response.ApiResponse;
import com.gserp.service.SalonSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SalonSettingsController {

    private final SalonSettingsService salonSettingsService;

    @GetMapping("/public")
    public ResponseEntity<ApiResponse<Map<String, String>>> getPublic() {
        return ResponseEntity.ok(ApiResponse.ok(salonSettingsService.getPublicSettings()));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, String>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(salonSettingsService.getPublicSettings()));
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> update(@RequestBody Map<String, String> body) {
        if (body.containsKey("name")) salonSettingsService.set("salon.name", body.get("name"));
        if (body.containsKey("logoUrl")) salonSettingsService.set("salon.logo_url", body.get("logoUrl"));
        if (body.containsKey("primaryColor")) salonSettingsService.set("salon.primary_color", body.get("primaryColor"));
        return ResponseEntity.ok(ApiResponse.ok("Ayarlar kaydedildi", null));
    }
}
