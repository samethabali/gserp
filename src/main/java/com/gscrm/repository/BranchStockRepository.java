package com.gscrm.repository;

import com.gscrm.model.BranchStock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BranchStockRepository extends JpaRepository<BranchStock, Long> {
    Optional<BranchStock> findBySalonIdAndProductId(Long salonId, Long productId);
}
