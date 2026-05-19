package com.gserp.controller;

import com.gserp.dto.response.ApiResponse;
import com.gserp.dto.response.CustomerDetailResponse;
import com.gserp.dto.response.CustomerResponse;
import com.gserp.model.Customer;
import com.gserp.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CustomerResponse>>> getAll(
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(ApiResponse.ok(customerService.getAll(q)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerDetailResponse>> getDetail(@PathVariable Long id) {
        return customerService.getDetail(id)
                .map(d -> ResponseEntity.ok(ApiResponse.ok(d)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Customer>> create(@RequestBody Customer customer) {
        return ResponseEntity.ok(ApiResponse.ok("Müşteri eklendi", customerService.create(customer)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Customer>> update(
            @PathVariable Long id, @RequestBody Customer customer) {
        return ResponseEntity.ok(ApiResponse.ok("Müşteri güncellendi", customerService.update(id, customer)));
    }

    @GetMapping("/lookup")
    public ResponseEntity<ApiResponse<CustomerResponse>> lookup(@RequestParam String phone) {
        return customerService.lookupByPhone(phone)
                .map(c -> ResponseEntity.ok(ApiResponse.ok(c)))
                .orElse(ResponseEntity.notFound().build());
    }
}
