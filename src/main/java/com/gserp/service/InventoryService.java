package com.gserp.service;

import com.gserp.dto.request.InventoryTransferRequest;
import com.gserp.model.BranchStock;
import com.gserp.model.Salon;
import com.gserp.repository.BranchStockRepository;
import com.gserp.repository.ProductRepository;
import com.gserp.repository.SalonRepository;
import com.gserp.security.AuthenticatedUser;
import com.gserp.security.BranchScopeService;
import com.gserp.security.StaffScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final BranchStockRepository branchStockRepository;
    private final ProductRepository productRepository;
    private final SalonRepository salonRepository;
    private final BranchScopeService branchScopeService;
    private final StaffScopeService staffScopeService;

    @Transactional
    public void transfer(InventoryTransferRequest request) {
        AuthenticatedUser user = staffScopeService.requireAuthenticatedUser();
        branchScopeService.assertCanAccessSalon(request.getFromSalonId(), user);
        branchScopeService.assertCanAccessSalon(request.getToSalonId(), user);

        Salon from = salonRepository.findById(request.getFromSalonId())
                .orElseThrow(() -> new IllegalArgumentException("Kaynak şube bulunamadı"));
        Salon to = salonRepository.findById(request.getToSalonId())
                .orElseThrow(() -> new IllegalArgumentException("Hedef şube bulunamadı"));
        if (!from.getOrganizationId().equals(to.getOrganizationId())) {
            throw new IllegalArgumentException("Şubeler aynı organizasyonda olmalı");
        }

        productRepository.findById(request.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Ürün bulunamadı"));

        BranchStock fromStock = branchStockRepository.findBySalonIdAndProductId(
                        request.getFromSalonId(), request.getProductId())
                .orElse(BranchStock.builder()
                        .salonId(request.getFromSalonId())
                        .productId(request.getProductId())
                        .quantity(0)
                        .build());

        if (fromStock.getQuantity() < request.getQuantity()) {
            throw new IllegalStateException("Yetersiz stok");
        }

        fromStock.setQuantity(fromStock.getQuantity() - request.getQuantity());
        fromStock.setUpdatedAt(LocalDateTime.now());
        branchStockRepository.save(fromStock);

        BranchStock toStock = branchStockRepository.findBySalonIdAndProductId(
                        request.getToSalonId(), request.getProductId())
                .orElse(BranchStock.builder()
                        .salonId(request.getToSalonId())
                        .productId(request.getProductId())
                        .quantity(0)
                        .build());
        toStock.setQuantity(toStock.getQuantity() + request.getQuantity());
        toStock.setUpdatedAt(LocalDateTime.now());
        branchStockRepository.save(toStock);
    }
}
