package com.gscrm.service;

import com.gscrm.model.OrganizationSubscription;
import com.gscrm.model.SubscriptionPlan;
import com.gscrm.repository.OrganizationSubscriptionRepository;
import com.gscrm.repository.SubscriptionPlanRepository;
import com.gscrm.repository.UsageMeterRepository;
import com.gscrm.repository.BillingEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private OrganizationSubscriptionRepository subscriptionRepository;
    @Mock
    private SubscriptionPlanRepository planRepository;
    @Mock
    private UsageMeterRepository usageMeterRepository;
    @Mock
    private BillingEventRepository billingEventRepository;

    @InjectMocks
    private SubscriptionService subscriptionService;

    /**
     * Abonelik satırı olmayan organizasyon süresiz ücretsiz yazabiliyordu
     * ({@code orElse(true)}); javadoc bunun düzeltildiğini söylerken kod hâlâ
     * fail-open'dı. Bu test davranışı kilitler.
     */
    @Test
    void isWriteAllowed_noSubscriptionRowDeniesWrite() {
        when(subscriptionRepository.findByOrganizationId(42L)).thenReturn(Optional.empty());
        assertThat(subscriptionService.isWriteAllowed(42L)).isFalse();
    }

    @Test
    void isWriteAllowed_activeSubscription() {
        when(subscriptionRepository.findByOrganizationId(1L)).thenReturn(Optional.of(
                OrganizationSubscription.builder().status("ACTIVE").build()));
        assertThat(subscriptionService.isWriteAllowed(1L)).isTrue();
    }

    @Test
    void isWriteAllowed_trialNotExpired() {
        when(subscriptionRepository.findByOrganizationId(1L)).thenReturn(Optional.of(
                OrganizationSubscription.builder()
                        .status("TRIAL")
                        .trialEnd(LocalDateTime.now().plusDays(5))
                        .build()));
        assertThat(subscriptionService.isWriteAllowed(1L)).isTrue();
    }

    @Test
    void isWriteAllowed_trialExpired() {
        when(subscriptionRepository.findByOrganizationId(1L)).thenReturn(Optional.of(
                OrganizationSubscription.builder()
                        .status("TRIAL")
                        .trialEnd(LocalDateTime.now().minusDays(1))
                        .build()));
        assertThat(subscriptionService.isWriteAllowed(1L)).isFalse();
    }

    @Test
    void getSubscriptionStatus_marksReadOnlyWhenTrialExpired() {
        OrganizationSubscription sub = OrganizationSubscription.builder()
                .status("TRIAL")
                .planId(10L)
                .trialEnd(LocalDateTime.now().minusHours(2))
                .build();
        when(subscriptionRepository.findByOrganizationId(2L)).thenReturn(Optional.of(sub));
        when(planRepository.findById(10L)).thenReturn(Optional.of(
                SubscriptionPlan.builder().code("SOLO").name("Solo").build()));

        Map<String, Object> status = subscriptionService.getSubscriptionStatus(2L);

        assertThat(status.get("readOnly")).isEqualTo(true);
        assertThat(status.get("status")).isEqualTo("TRIAL");
        assertThat(status.get("planCode")).isEqualTo("SOLO");
    }
}
