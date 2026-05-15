package com.gserp.controller;

import com.gserp.dto.response.ApiResponse;
import com.gserp.model.AuditLogEntry;
import com.gserp.store.MockDataStore;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final MockDataStore store;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AuditLogEntry>>> getRecent(
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(ApiResponse.ok(store.getRecentAuditLogs(limit)));
    }
}
