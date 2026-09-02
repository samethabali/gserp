package com.gscrm.repository;

import com.gscrm.model.BranchServicePrice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BranchServicePriceRepository extends JpaRepository<BranchServicePrice, Long> {
    Optional<BranchServicePrice> findBySalonIdAndServiceIdAndActiveTrue(Long salonId, Long serviceId);
}
