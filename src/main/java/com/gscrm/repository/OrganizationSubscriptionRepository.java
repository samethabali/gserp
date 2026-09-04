package com.gscrm.repository;

import com.gscrm.model.OrganizationSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrganizationSubscriptionRepository extends JpaRepository<OrganizationSubscription, Long> {

    Optional<OrganizationSubscription> findByOrganizationId(Long organizationId);

    List<OrganizationSubscription> findByOrganizationIdIn(Collection<Long> organizationIds);

    /**
     * Süresi dolmuş denemeler.
     *
     * <p>{@code TrialStatusJob} ve deneme sonu uyarıcısı bu sorguyu kullanır;
     * ikisi de daha önce tüm abonelikleri belleğe çekip orada filtreliyordu.
     */
    List<OrganizationSubscription> findByStatusAndTrialEndBefore(String status, LocalDateTime cutoff);

    List<OrganizationSubscription> findByStatusAndTrialEndBetween(String status, LocalDateTime from, LocalDateTime to);
}
