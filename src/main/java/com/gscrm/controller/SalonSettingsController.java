package com.gscrm.controller;

import com.gscrm.dto.request.WhatsAppSettingsUpdateRequest;
import com.gscrm.dto.response.ApiResponse;
import com.gscrm.dto.response.WhatsAppSettingsResponse;
import com.gscrm.service.SalonSettingsService;
import com.gscrm.service.SalonWhatsAppService;
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
    private final SalonWhatsAppService salonWhatsAppService;

    @GetMapping("/public")
    public ResponseEntity<ApiResponse<Map<String, String>>> getPublic() {
        return ResponseEntity.ok(ApiResponse.ok(salonSettingsService.getPublicSettings()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','BRANCH_MANAGER','ORG_OWNER','PLATFORM_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, String>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(salonSettingsService.getPublicSettings()));
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN','BRANCH_MANAGER','ORG_OWNER','PLATFORM_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> update(@RequestBody Map<String, String> body) {
        if (body.containsKey("name")) salonSettingsService.set("salon.name", body.get("name"));
        if (body.containsKey("logoUrl")) salonSettingsService.set("salon.logo_url", body.get("logoUrl"));
        if (body.containsKey("primaryColor")) salonSettingsService.set("salon.primary_color", body.get("primaryColor"));
        return ResponseEntity.ok(ApiResponse.ok("Ayarlar kaydedildi", null));
    }

    @GetMapping("/whatsapp")
    @PreAuthorize("hasAnyRole('ADMIN','BRANCH_MANAGER','ORG_OWNER','PLATFORM_ADMIN')")
    public ResponseEntity<ApiResponse<WhatsAppSettingsResponse>> getWhatsApp() {
        return ResponseEntity.ok(ApiResponse.ok(salonWhatsAppService.getSettingsForCurrentSalon()));
    }

    @PutMapping("/whatsapp")
    @PreAuthorize("hasAnyRole('ADMIN','BRANCH_MANAGER','ORG_OWNER','PLATFORM_ADMIN')")
    public ResponseEntity<ApiResponse<WhatsAppSettingsResponse>> updateWhatsApp(
            @RequestBody WhatsAppSettingsUpdateRequest body) {
        WhatsAppSettingsResponse updated = salonWhatsAppService.updateForCurrentSalon(body);
        return ResponseEntity.ok(ApiResponse.ok("WhatsApp ayarları kaydedildi", updated));
    }
}
