package com.gscrm.repository;

import com.gscrm.model.BillingEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingEventRepository extends JpaRepository<BillingEvent, Long> {
}
