package com.gscrm.controller;

import com.gscrm.dto.response.ApiResponse;
import com.gscrm.service.SalonSettingsService;
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
    @PreAuthorize("hasAnyRole('ADMIN','BRANCH_MANAGER','ORG_OWNER','PLATFORM_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, String>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(salonSettingsService.getManagementSettings()));
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN','BRANCH_MANAGER','ORG_OWNER','PLATFORM_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> update(@RequestBody Map<String, String> body) {
        if (body.containsKey("name")) salonSettingsService.updateSalonName(body.get("name"));
        if (body.containsKey("logoUrl")) salonSettingsService.updateLogo(body.get("logoUrl"));
        if (body.containsKey("primaryColor")) salonSettingsService.set("salon.primary_color", body.get("primaryColor"));
        if (body.containsKey("smsVerificationEnabled")) {
            // Değeri normalize et: ayar okuyan taraf Boolean.parseBoolean kullanıyor,
            // serbest metin sessizce "false" anlamına gelirdi.
            salonSettingsService.set("booking.sms_verification_enabled",
                    Boolean.parseBoolean(body.get("smsVerificationEnabled")) ? "true" : "false");
        }
        return ResponseEntity.ok(ApiResponse.ok("Ayarlar kaydedildi", null));
    }
}
