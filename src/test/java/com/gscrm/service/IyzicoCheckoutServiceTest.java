package com.gscrm.service;

import com.gscrm.config.IyzicoProperties;
import com.gscrm.model.OrganizationSubscription;
import com.gscrm.model.SubscriptionPlan;
import com.gscrm.repository.OrganizationSubscriptionRepository;
import com.gscrm.repository.SubscriptionPlanRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IyzicoCheckoutServiceTest {

    @Mock private IyzicoProperties iyzicoProperties;
    @Mock private OrganizationSubscriptionRepository subscriptionRepository;
    @Mock private SubscriptionPlanRepository planRepository;
    @Mock private SubscriptionService subscriptionService;
    @Mock private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @InjectMocks
    private IyzicoCheckoutService iyzicoCheckoutService;

    @Test
    void initiateCheckout_mockMode() {
        when(iyzicoProperties.isEnabled()).thenReturn(false);
        when(subscriptionRepository.findByOrganizationId(5L)).thenReturn(Optional.of(
                OrganizationSubscription.builder().organizationId(5L).planId(1L).status("TRIAL").build()));
        when(planRepository.findById(1L)).thenReturn(Optional.of(
                SubscriptionPlan.builder().id(1L).code("SOLO").name("Solo").priceMonthly(new BigDecimal("990")).build()));

        Map<String, Object> result = iyzicoCheckoutService.initiateCheckout(5L);

        assertThat(result.get("mock")).isEqualTo(true);
        verify(subscriptionService).recordBillingEvent(eq(5L), eq("CHECKOUT_INIT_MOCK"), anyString());
    }
}
