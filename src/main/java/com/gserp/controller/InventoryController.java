package com.gserp.controller;

import com.gserp.dto.request.InventoryTransferRequest;
import com.gserp.dto.response.ApiResponse;
import com.gserp.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','BRANCH_MANAGER','ORG_OWNER','PLATFORM_ADMIN')")
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping("/transfer")
    public ResponseEntity<ApiResponse<Void>> transfer(@Valid @RequestBody InventoryTransferRequest request) {
        inventoryService.transfer(request);
        return ResponseEntity.ok(ApiResponse.ok("Stok transferi tamamlandı", null));
    }
}
