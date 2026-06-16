package com.gserp.controller;

import com.gserp.dto.response.ApiResponse;
import com.gserp.model.WaitlistEntry;
import com.gserp.service.WaitlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/waitlist")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST')")
public class WaitlistController {

    private final WaitlistService waitlistService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<WaitlistEntry>>> getActive() {
        return ResponseEntity.ok(ApiResponse.ok(waitlistService.getActive()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WaitlistEntry>> add(@RequestBody WaitlistEntry entry) {
        return ResponseEntity.ok(ApiResponse.ok("Bekleme listesine eklendi", waitlistService.add(entry)));
    }

    @PatchMapping("/{id}/fulfill")
    public ResponseEntity<ApiResponse<Void>> fulfill(@PathVariable Long id) {
        waitlistService.fulfill(id);
        return ResponseEntity.ok(ApiResponse.ok("Bekleme listesi kaydı tamamlandı", null));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        waitlistService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Bekleme listesinden silindi", null));
    }
}
