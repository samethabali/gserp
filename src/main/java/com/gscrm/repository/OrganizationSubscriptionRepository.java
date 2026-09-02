package com.gscrm.repository;

import com.gscrm.model.OrganizationSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrganizationSubscriptionRepository extends JpaRepository<OrganizationSubscription, Long> {
    Optional<OrganizationSubscription> findByOrganizationId(Long organizationId);
}
