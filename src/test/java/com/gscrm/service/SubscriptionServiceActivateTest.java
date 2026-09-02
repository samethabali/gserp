package com.gscrm.service;

import com.gscrm.model.OrganizationSubscription;
import com.gscrm.repository.BillingEventRepository;
import com.gscrm.repository.OrganizationSubscriptionRepository;
import com.gscrm.repository.SalonRepository;
import com.gscrm.repository.SubscriptionPlanRepository;
import com.gscrm.repository.UsageMeterRepository;
import com.gscrm.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceActivateTest {

    @Mock private OrganizationSubscriptionRepository subscriptionRepository;
    @Mock private SubscriptionPlanRepository planRepository;
    @Mock private UsageMeterRepository usageMeterRepository;
    @Mock private BillingEventRepository billingEventRepository;
    @Mock private SalonRepository salonRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private SubscriptionService subscriptionService;

    @Test
    void activateSubscription_setsActive() {
        OrganizationSubscription sub = OrganizationSubscription.builder()
                .organizationId(3L).planId(1L).status("TRIAL").trialEnd(LocalDateTime.now().plusDays(2)).build();
        when(subscriptionRepository.findByOrganizationId(3L)).thenReturn(Optional.of(sub));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(billingEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        subscriptionService.activateSubscription(3L, "pay-123");

        assertThat(sub.getStatus()).isEqualTo("ACTIVE");
        assertThat(sub.getExternalId()).isEqualTo("pay-123");
        verify(subscriptionRepository).save(sub);
    }
}
