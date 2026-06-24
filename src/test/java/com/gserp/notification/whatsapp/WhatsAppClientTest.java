package com.gserp.notification.whatsapp;

import com.gserp.model.NotificationLog;
import com.gserp.model.SalonWhatsAppConfig;
import com.gserp.repository.NotificationLogRepository;
import com.gserp.repository.SalonRepository;
import com.gserp.service.QuotaEnforcementService;
import com.gserp.service.SalonWhatsAppService;
import com.gserp.service.SubscriptionService;
import com.gserp.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsAppClientTest {

    private static final Long SALON_ID = 1L;
    private static final Long ORG_ID = 10L;

    @Mock
    private WhatsAppProperties properties;
    @Mock
    private SalonWhatsAppService salonWhatsAppService;
    @Mock
    private SalonRepository salonRepository;
    @Mock
    private QuotaEnforcementService quotaEnforcementService;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private NotificationLogRepository notificationLogRepository;

    @InjectMocks
    private WhatsAppClient whatsAppClient;

    @BeforeEach
    void setTenant() {
        TenantContext.setSalonId(SALON_ID);
        TenantContext.setOrgId(ORG_ID);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void sendTemplate_skipsWhenQuotaExceeded() {
        when(salonWhatsAppService.getForCurrentSalon()).thenReturn(Optional.of(
                SalonWhatsAppConfig.builder()
                        .salonId(SALON_ID)
                        .enabled(true)
                        .tokenEnc("token")
                        .phoneNumberId("phone-id")
                        .build()));
        when(properties.getApiUrl()).thenReturn("https://graph.facebook.com/v18.0");
        doThrow(new IllegalStateException("WhatsApp kotası doldu"))
                .when(quotaEnforcementService).assertWhatsAppQuota(ORG_ID);

        whatsAppClient.sendTemplate(99L, "+905551112233", "appointment_reminder", List.of("Ali"));

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository).save(captor.capture());
        assertEquals("SKIPPED_QUOTA", captor.getValue().getStatus());
        verify(subscriptionService, never()).incrementUsage(any(), any(), any(), eq(1));
    }

    @Test
    void sendTemplate_skipsWhenDisabled() {
        when(salonWhatsAppService.getForCurrentSalon()).thenReturn(Optional.empty());
        when(properties.isEnabled()).thenReturn(false);

        whatsAppClient.sendTemplate(99L, "+905551112233", "appointment_reminder", List.of("Ali"));

        verify(notificationLogRepository, never()).save(any());
        verify(quotaEnforcementService, never()).assertWhatsAppQuota(any());
    }
}
