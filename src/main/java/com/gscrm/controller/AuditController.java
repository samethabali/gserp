package com.gscrm.controller;

import com.gscrm.dto.response.ApiResponse;
import com.gscrm.model.AuditLogEntry;
import com.gscrm.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','BRANCH_MANAGER','ORG_OWNER','PLATFORM_ADMIN')")
    public ResponseEntity<ApiResponse<List<AuditLogEntry>>> getRecent(
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(ApiResponse.ok(auditService.getRecent(limit)));
    }
}
