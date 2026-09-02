package com.gscrm.service;

import com.gscrm.dto.request.InventoryTransferRequest;
import com.gscrm.model.BranchStock;
import com.gscrm.model.Salon;
import com.gscrm.repository.BranchStockRepository;
import com.gscrm.repository.ProductRepository;
import com.gscrm.repository.SalonRepository;
import com.gscrm.security.AuthenticatedUser;
import com.gscrm.security.BranchScopeService;
import com.gscrm.security.StaffScopeService;
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
