package com.gscrm.service;

import com.gscrm.model.OrganizationSubscription;
import com.gscrm.repository.OrganizationSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Süresi dolan denemeleri {@code PAST_DUE} durumuna geçirir.
 *
 * <p>{@code status} alanı daha önce hiçbir zaman {@code TRIAL}'dan çıkmıyordu:
 * bitişin geçip geçmediği yalnızca okuma anında hesaplanıyordu. Yazma kapısı bu
 * anlık hesabı kullandığı için doğru davranıyor, ama panel ve raporlar süresi
 * dolmuş bir işletmeyi hâlâ "deneme" olarak gösteriyordu. Durum artık kaydediliyor;
 * geçişin kendisi de {@code billing_event} olarak iz bırakıyor.
 *
 * <p>Yazma kapatma davranışı değişmez: {@code SubscriptionReadOnlyFilter} okumayı
 * açık bırakıp yazma uçlarında HTTP 402 döndürmeye devam eder.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrialStatusJob {

    static final String STATUS_TRIAL = "TRIAL";
    static final String STATUS_PAST_DUE = "PAST_DUE";
    static final String EVENT_TYPE = "TRIAL_EXPIRED";

    private final OrganizationSubscriptionRepository subscriptionRepository;
    private final SubscriptionService subscriptionService;

    /** Gece yarısından hemen sonra: gün dönümünde biten denemeler aynı gün işaretlenir. */
    @Scheduled(cron = "0 5 0 * * *", zone = "Europe/Istanbul")
    @Transactional
    public void expireFinishedTrials() {
        LocalDateTime now = LocalDateTime.now();
        List<OrganizationSubscription> expired =
                subscriptionRepository.findByStatusAndTrialEndBefore(STATUS_TRIAL, now);
        if (expired.isEmpty()) {
            return;
        }
        for (OrganizationSubscription sub : expired) {
            sub.setStatus(STATUS_PAST_DUE);
            sub.setUpdatedAt(now);
            subscriptionRepository.save(sub);
            subscriptionService.recordBillingEvent(sub.getOrganizationId(), EVENT_TYPE,
                    "Ücretsiz kullanım süresi doldu (" + sub.getTrialEnd() + ")");
        }
        log.info("Deneme süresi dolan {} abonelik PAST_DUE yapıldı.", expired.size());
    }
}
