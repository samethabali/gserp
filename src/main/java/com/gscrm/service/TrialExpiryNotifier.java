package com.gscrm.service;

import com.gscrm.model.OrganizationSubscription;
import com.gscrm.repository.OrganizationSubscriptionRepository;
import com.gscrm.repository.OrganizationRepository;
import com.gscrm.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrialExpiryNotifier {

    /**
     * Hatırlatma eşikleri (kalan gün).
     *
     * <p>Deneme süresi 90 güne çıktı; yalnızca D-3/D-1/D-0 uyarmak, kullanıcıya
     * karar vermek için üç gün bırakıyordu. D-30 ilk uyarı, D-1 son uyarıdır.
     */
    private static final Set<Long> REMINDER_DAYS = Set.of(30L, 7L, 3L, 1L);

    private final OrganizationSubscriptionRepository subscriptionRepository;
    private final OrganizationRepository organizationRepository;
    private final SubscriptionService subscriptionService;

    @Scheduled(cron = "0 0 9 * * *", zone = "Europe/Istanbul")
    @Transactional
    public void notifyExpiringTrials() {
        LocalDateTime now = LocalDateTime.now();
        // Yalnızca en uzak eşiğe kadar olan pencere okunur; iş eskiden bütün
        // abonelikleri belleğe çekip orada eliyordu.
        List<OrganizationSubscription> trials = subscriptionRepository.findByStatusAndTrialEndBetween(
                "TRIAL", now, now.plusDays(31));

        for (OrganizationSubscription sub : trials) {
            long daysLeft = ChronoUnit.DAYS.between(now, sub.getTrialEnd());
            if (!REMINDER_DAYS.contains(daysLeft)) {
                continue;
            }
            String eventType = "TRIAL_REMINDER_D" + daysLeft;
            if (subscriptionService.hasBillingEvent(sub.getOrganizationId(), eventType)) {
                continue;
            }
            String orgName = organizationRepository.findById(sub.getOrganizationId())
                    .map(o -> o.getName())
                    .orElse("Org#" + sub.getOrganizationId());
            String message = "Deneme süreniz " + daysLeft + " gün içinde bitiyor: " + orgName;
            log.info("TRIAL REMINDER: {}", message);
            subscriptionService.recordBillingEvent(sub.getOrganizationId(), eventType, message);
        }
    }
}
