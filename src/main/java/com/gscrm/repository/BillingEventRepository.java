package com.gscrm.repository;

import com.gscrm.model.BillingEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingEventRepository extends JpaRepository<BillingEvent, Long> {

    boolean existsByOrganizationIdAndEventType(Long organizationId, String eventType);

    java.util.List<BillingEvent> findTop20ByOrganizationIdOrderByCreatedAtDesc(Long organizationId);
}
