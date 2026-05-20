package com.gserp.repository;

import com.gserp.model.LoyaltyTier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoyaltyTierRepository extends JpaRepository<LoyaltyTier, Long> {
    List<LoyaltyTier> findByActiveTrueOrderByMinCompletedDesc();
}
