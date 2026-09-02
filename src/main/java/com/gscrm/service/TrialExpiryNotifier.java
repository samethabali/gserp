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

@Slf4j
@Service
@RequiredArgsConstructor
public class TrialExpiryNotifier {

    private final OrganizationSubscriptionRepository subscriptionRepository;
    private final OrganizationRepository organizationRepository;
    private final SubscriptionService subscriptionService;

    @Scheduled(cron = "0 0 9 * * *", zone = "Europe/Istanbul")
    @Transactional
    public void notifyExpiringTrials() {
        LocalDateTime now = LocalDateTime.now();
        List<OrganizationSubscription> trials = subscriptionRepository.findAll().stream()
                .filter(s -> "TRIAL".equals(s.getStatus()))
                .filter(s -> s.getTrialEnd() != null)
                .toList();

        for (OrganizationSubscription sub : trials) {
            long daysLeft = ChronoUnit.DAYS.between(now, sub.getTrialEnd());
            if (daysLeft != 3 && daysLeft != 1 && daysLeft != 0) {
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
