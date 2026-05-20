package com.gserp.repository;

import com.gserp.model.CouponUsage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponUsageRepository extends JpaRepository<CouponUsage, Long> {
    long countByCouponId(Long couponId);
    boolean existsByCouponIdAndCustomerId(Long couponId, Long customerId);
}
