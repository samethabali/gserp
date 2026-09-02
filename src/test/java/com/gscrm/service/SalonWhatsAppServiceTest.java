package com.gscrm.service;

import com.gscrm.dto.request.WhatsAppSettingsUpdateRequest;
import com.gscrm.dto.response.WhatsAppSettingsResponse;
import com.gscrm.model.SalonWhatsAppConfig;
import com.gscrm.notification.whatsapp.WhatsAppProperties;
import com.gscrm.repository.SalonWhatsAppConfigRepository;
import com.gscrm.security.SecretEncryptionService;
import com.gscrm.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalonWhatsAppServiceTest {

    private static final Long SALON_ID = 1L;

    @Mock
    private SalonWhatsAppConfigRepository configRepository;
    @Mock
    private WhatsAppProperties whatsAppProperties;
    @Mock
    private SecretEncryptionService secretEncryptionService;

    @InjectMocks
    private SalonWhatsAppService salonWhatsAppService;

    @BeforeEach
    void setTenant() {
        TenantContext.setSalonId(SALON_ID);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void getSettingsForCurrentSalon_returnsDefaultsWhenNoConfig() {
        when(configRepository.findBySalonId(SALON_ID)).thenReturn(Optional.empty());
        when(whatsAppProperties.isEnabled()).thenReturn(false);

        WhatsAppSettingsResponse settings = salonWhatsAppService.getSettingsForCurrentSalon();

        assertFalse(settings.isEnabled());
        assertFalse(settings.isTokenConfigured());
        assertFalse(settings.isGlobalFallbackEnabled());
        assertEquals("", settings.getPhoneNumberId());
    }

    @Test
    void updateForCurrentSalon_persistsSalonConfig() {
        when(configRepository.findBySalonId(SALON_ID)).thenReturn(Optional.empty());
        when(configRepository.save(any(SalonWhatsAppConfig.class))).thenAnswer(inv -> inv.getArgument(0));
        when(whatsAppProperties.isEnabled()).thenReturn(false);
        when(secretEncryptionService.encrypt("secret-token")).thenReturn("enc:secret-token");

        WhatsAppSettingsUpdateRequest req = new WhatsAppSettingsUpdateRequest();
        req.setEnabled(true);
        req.setToken("secret-token");
        req.setPhoneNumberId("123456");
        req.setSalonPhoneE164("+905551112233");

        salonWhatsAppService.updateForCurrentSalon(req);

        ArgumentCaptor<SalonWhatsAppConfig> captor = ArgumentCaptor.forClass(SalonWhatsAppConfig.class);
        verify(configRepository).save(captor.capture());
        SalonWhatsAppConfig saved = captor.getValue();
        assertTrue(saved.isEnabled());
        assertEquals("enc:secret-token", saved.getTokenEnc());
        assertEquals("123456", saved.getPhoneNumberId());
        assertEquals("+905551112233", saved.getSalonPhoneE164());
    }

    @Test
    void updateForCurrentSalon_rejectsEnableWithoutToken() {
        SalonWhatsAppConfig existing = SalonWhatsAppConfig.builder()
                .salonId(SALON_ID)
                .enabled(false)
                .phoneNumberId("999")
                .build();
        when(configRepository.findBySalonId(SALON_ID)).thenReturn(Optional.of(existing));

        WhatsAppSettingsUpdateRequest req = new WhatsAppSettingsUpdateRequest();
        req.setEnabled(true);

        assertThrows(IllegalArgumentException.class, () -> salonWhatsAppService.updateForCurrentSalon(req));
        verify(configRepository, never()).save(any());
    }

    @Test
    void updateForCurrentSalon_preservesExistingTokenWhenBlank() {
        SalonWhatsAppConfig existing = SalonWhatsAppConfig.builder()
                .salonId(SALON_ID)
                .enabled(true)
                .tokenEnc("stored-token")
                .phoneNumberId("123456")
                .build();
        when(configRepository.findBySalonId(SALON_ID)).thenReturn(Optional.of(existing));
        when(configRepository.save(any(SalonWhatsAppConfig.class))).thenAnswer(inv -> inv.getArgument(0));
        when(whatsAppProperties.isEnabled()).thenReturn(false);

        WhatsAppSettingsUpdateRequest req = new WhatsAppSettingsUpdateRequest();
        req.setSalonPhoneE164("+905551112233");

        salonWhatsAppService.updateForCurrentSalon(req);

        ArgumentCaptor<SalonWhatsAppConfig> captor = ArgumentCaptor.forClass(SalonWhatsAppConfig.class);
        verify(configRepository).save(captor.capture());
        assertEquals("stored-token", captor.getValue().getTokenEnc());
    }
}
