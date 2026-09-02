package com.gscrm.controller;

import com.gscrm.dto.response.ApiResponse;
import com.gscrm.model.Product;
import com.gscrm.model.ProductSale;
import com.gscrm.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','BRANCH_MANAGER','ORG_OWNER','PLATFORM_ADMIN','RECEPTIONIST')")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Product>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(productService.getAll()));
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<Product>>> getActive() {
        return ResponseEntity.ok(ApiResponse.ok(productService.getActive()));
    }

    @GetMapping("/low-stock")
    public ResponseEntity<ApiResponse<List<Product>>> getLowStock() {
        return ResponseEntity.ok(ApiResponse.ok(productService.getLowStock()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Product>> create(@RequestBody Product product) {
        return ResponseEntity.ok(ApiResponse.ok("Ürün eklendi", productService.create(product)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Product>> update(
            @PathVariable Long id, @RequestBody Product product) {
        return ResponseEntity.ok(ApiResponse.ok("Ürün güncellendi", productService.update(id, product)));
    }

    @PostMapping("/{id}/sell")
    public ResponseEntity<ApiResponse<ProductSale>> sell(
            @PathVariable Long id, @RequestBody Map<String, Object> body) {
        int qty           = Integer.parseInt(body.getOrDefault("quantity", 1).toString());
        Long appointmentId = body.get("appointmentId") != null ? Long.valueOf(body.get("appointmentId").toString()) : null;
        Long customerId    = body.get("customerId")    != null ? Long.valueOf(body.get("customerId").toString())    : null;
        String phone      = body.get("customerPhone") != null ? body.get("customerPhone").toString() : null;
        String name       = body.get("customerName")  != null ? body.get("customerName").toString()  : null;
        Long staffId      = body.get("staffId")        != null ? Long.valueOf(body.get("staffId").toString()) : null;
        return ResponseEntity.ok(ApiResponse.ok("Satış kaydedildi",
                productService.sell(id, qty, appointmentId, customerId, phone, name, staffId)));
    }

    @PutMapping("/{id}/restock")
    public ResponseEntity<ApiResponse<Product>> restock(
            @PathVariable Long id, @RequestBody Map<String, Integer> body) {
        int qty = body.getOrDefault("quantity", 0);
        return ResponseEntity.ok(ApiResponse.ok("Stok güncellendi", productService.restock(id, qty)));
    }

    @GetMapping("/sales")
    public ResponseEntity<ApiResponse<List<ProductSale>>> getSales(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate startDate = from != null ? from : LocalDate.now().minusDays(30);
        LocalDate endDate = to != null ? to : LocalDate.now();
        return ResponseEntity.ok(ApiResponse.ok(productService.getSalesByDateRange(startDate, endDate)));
    }

    @GetMapping("/sales/stats")
    public ResponseEntity<ApiResponse<com.gscrm.dto.response.ProductSaleStatsResponse>> getSalesStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate startDate = from != null ? from : LocalDate.now().minusDays(30);
        LocalDate endDate = to != null ? to : LocalDate.now();
        return ResponseEntity.ok(ApiResponse.ok(productService.getSalesStats(startDate, endDate)));
    }
}
