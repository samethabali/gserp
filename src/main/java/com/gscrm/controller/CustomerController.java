package com.gscrm.controller;

import com.gscrm.dto.request.CustomerCreateRequest;
import com.gscrm.dto.response.ApiResponse;
import com.gscrm.dto.response.CustomerDetailResponse;
import com.gscrm.dto.response.CustomerResponse;
import com.gscrm.dto.response.RecentCustomerDto;
import com.gscrm.model.Customer;
import com.gscrm.model.ConsentRecord;
import com.gscrm.service.ActivityEventService;
import com.gscrm.service.ConsentService;
import com.gscrm.service.CustomerMatchingService;
import com.gscrm.service.CustomerService;
import com.gscrm.service.GdprService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','BRANCH_MANAGER','ORG_OWNER','PLATFORM_ADMIN','RECEPTIONIST')")
public class CustomerController {

    private final CustomerService customerService;
    private final CustomerMatchingService customerMatchingService;
    private final GdprService gdprService;
    private final ConsentService consentService;
    private final ActivityEventService activityEventService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CustomerResponse>>> getAll(
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(ApiResponse.ok(customerService.getAll(q)));
    }

    @GetMapping("/recent")
    public ResponseEntity<ApiResponse<List<RecentCustomerDto>>> recent(
            @RequestParam(defaultValue = "8") int limit) {
        return ResponseEntity.ok(ApiResponse.ok(customerService.getRecentCustomers(limit)));
    }

    /**
     * Müşteri kartı.
     *
     * <p>KVKK kapsamında kişisel veriye erişim iz bırakmalı: kartın açılması
     * {@code VIEW} olarak kütüğe yazılır. Genel listeler bilinçli olarak
     * loglanmaz — her sayfa yenilemesi satır üretir ve kütüğü okunamaz hale getirir.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerDetailResponse>> getDetail(@PathVariable Long id) {
        return customerService.getDetail(id)
                .map(d -> {
                    activityEventService.record("VIEW", "CUSTOMER", id, id,
                            "Müşteri kartı görüntülendi");
                    return ResponseEntity.ok(ApiResponse.ok(d));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Customer>> create(@Valid @RequestBody CustomerCreateRequest request) {
        Customer customer = Customer.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .email(request.getEmail())
                .notes(request.getNotes())
                .build();
        return ResponseEntity.ok(ApiResponse.ok("Müşteri eklendi", customerService.create(customer, request.isAllowDuplicate())));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Customer>> update(
            @PathVariable Long id, @Valid @RequestBody CustomerCreateRequest request) {
        Customer customer = Customer.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .email(request.getEmail())
                .notes(request.getNotes())
                .build();
        return ResponseEntity.ok(ApiResponse.ok("Müşteri güncellendi", customerService.update(id, customer, request.isAllowDuplicate())));
    }

    /**
     * Aynı normalize telefonu paylaşan müşteri grupları.
     *
     * <p>Birleştirme bilinçli olarak yok: gerçek bir birleştirme randevu, tahsilat,
     * ürün satışı ve rıza kayıtlarını taşıyıp bakiyeyi mutabık kılmayı gerektirir.
     * Burada yalnızca tespit var, kararı salon veriyor.
     */
    @GetMapping("/duplicates")
    public ResponseEntity<ApiResponse<List<CustomerMatchingService.DuplicateGroup>>> duplicates() {
        return ResponseEntity.ok(ApiResponse.ok(customerMatchingService.findDuplicateGroups()));
    }

    @GetMapping("/lookup")
    public ResponseEntity<ApiResponse<CustomerResponse>> lookup(@RequestParam String phone) {
        return customerService.lookupByPhone(phone)
                .map(c -> {
                    activityEventService.record("VIEW", "CUSTOMER", c.getId(), c.getId(),
                            "Müşteri telefonla sorgulandı");
                    return ResponseEntity.ok(ApiResponse.ok(c));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<ApiResponse<Map<String, Object>>> export(@PathVariable Long id) {
        Map<String, Object> data = gdprService.exportCustomer(id);
        activityEventService.record("EXPORT", "CUSTOMER", id, id,
                "Müşteri verisi dışa aktarıldı (KVKK)");
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @DeleteMapping("/{id}/gdpr")
    @PreAuthorize("hasAnyRole('ADMIN','BRANCH_MANAGER')")
    public ResponseEntity<ApiResponse<Void>> anonymize(@PathVariable Long id) {
        gdprService.anonymizeCustomer(id);
        return ResponseEntity.ok(ApiResponse.ok("Müşteri anonimleştirildi", null));
    }

    @PostMapping("/{id}/consent/revoke")
    public ResponseEntity<ApiResponse<Void>> revokeConsent(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String type = body.getOrDefault("consentType", "MARKETING");
        consentService.revokeConsent(id, type);
        return ResponseEntity.ok(ApiResponse.ok("Onay geri alındı", null));
    }

    @GetMapping("/{id}/consent")
    public ResponseEntity<ApiResponse<List<ConsentRecord>>> listConsents(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(consentService.listConsents(id)));
    }

    @GetMapping("/{id}/activity")
    public ResponseEntity<ApiResponse<List<com.gscrm.model.ActivityEvent>>> activity(
            @PathVariable Long id,
            @RequestParam(defaultValue = "50") int limit) {
        if (customerService.getDetail(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ApiResponse.ok(activityEventService.listForCustomer(id, limit)));
    }
}
