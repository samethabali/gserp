package com.gscrm.service;

import com.gscrm.dto.request.WhatsAppSettingsUpdateRequest;
import com.gscrm.dto.response.WhatsAppSettingsResponse;
import com.gscrm.model.SalonWhatsAppConfig;
import com.gscrm.security.SecretEncryptionService;
import com.gscrm.notification.whatsapp.WhatsAppProperties;
import com.gscrm.repository.SalonWhatsAppConfigRepository;
import com.gscrm.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalonWhatsAppService {

    private final SalonWhatsAppConfigRepository configRepository;
    private final WhatsAppProperties whatsAppProperties;
    private final SecretEncryptionService secretEncryptionService;

    public Optional<SalonWhatsAppConfig> getForCurrentSalon() {
        Long salonId = TenantContext.getSalonId();
        if (salonId == null) {
            return Optional.empty();
        }
        return configRepository.findBySalonId(salonId);
    }

    public Optional<SalonWhatsAppConfig> resolveByPhoneNumberId(String phoneNumberId) {
        if (phoneNumberId == null || phoneNumberId.isBlank()) {
            return Optional.empty();
        }
        return configRepository.findByPhoneNumberId(phoneNumberId);
    }

    @Transactional
    public SalonWhatsAppConfig saveForSalon(Long salonId, SalonWhatsAppConfig patch) {
        SalonWhatsAppConfig config = configRepository.findBySalonId(salonId)
                .orElse(SalonWhatsAppConfig.builder().salonId(salonId).build());
        config.setEnabled(patch.isEnabled());
        if (patch.getTokenEnc() != null) config.setTokenEnc(secretEncryptionService.encrypt(patch.getTokenEnc()));
        if (patch.getPhoneNumberId() != null) config.setPhoneNumberId(patch.getPhoneNumberId());
        if (patch.getBusinessAccountId() != null) config.setBusinessAccountId(patch.getBusinessAccountId());
        if (patch.getSalonPhoneE164() != null) config.setSalonPhoneE164(patch.getSalonPhoneE164());
        if (patch.getWebhookVerifyToken() != null) config.setWebhookVerifyToken(patch.getWebhookVerifyToken());
        config.setUpdatedAt(LocalDateTime.now());
        return configRepository.save(config);
    }

    public WhatsAppSettingsResponse getSettingsForCurrentSalon() {
        Optional<SalonWhatsAppConfig> config = getForCurrentSalon();
        return WhatsAppSettingsResponse.builder()
                .enabled(config.map(SalonWhatsAppConfig::isEnabled).orElse(false))
                .tokenConfigured(config.map(c -> c.getTokenEnc() != null && !c.getTokenEnc().isBlank()).orElse(false))
                .globalFallbackEnabled(whatsAppProperties.isEnabled())
                .phoneNumberId(config.map(SalonWhatsAppConfig::getPhoneNumberId).orElse(""))
                .businessAccountId(config.map(SalonWhatsAppConfig::getBusinessAccountId).orElse(""))
                .salonPhoneE164(config.map(SalonWhatsAppConfig::getSalonPhoneE164).orElse(""))
                .webhookVerifyToken(config.map(SalonWhatsAppConfig::getWebhookVerifyToken).orElse(""))
                .updatedAt(config.map(SalonWhatsAppConfig::getUpdatedAt).orElse(null))
                .build();
    }

    @Transactional
    public WhatsAppSettingsResponse updateForCurrentSalon(WhatsAppSettingsUpdateRequest req) {
        Long salonId = TenantContext.requireSalonId();
        SalonWhatsAppConfig config = configRepository.findBySalonId(salonId)
                .orElse(SalonWhatsAppConfig.builder().salonId(salonId).build());

        if (req.getEnabled() != null) {
            config.setEnabled(req.getEnabled());
        }
        if (req.getToken() != null && !req.getToken().isBlank()) {
            config.setTokenEnc(secretEncryptionService.encrypt(req.getToken().trim()));
        }
        if (req.getPhoneNumberId() != null) {
            config.setPhoneNumberId(req.getPhoneNumberId().trim());
        }
        if (req.getBusinessAccountId() != null) {
            config.setBusinessAccountId(req.getBusinessAccountId().trim());
        }
        if (req.getSalonPhoneE164() != null) {
            config.setSalonPhoneE164(req.getSalonPhoneE164().trim());
        }
        if (req.getWebhookVerifyToken() != null) {
            config.setWebhookVerifyToken(req.getWebhookVerifyToken().trim());
        }

        if (config.isEnabled()) {
            if (config.getPhoneNumberId() == null || config.getPhoneNumberId().isBlank()) {
                throw new IllegalArgumentException("WhatsApp etkinleştirmek için Phone Number ID gerekli");
            }
            if (config.getTokenEnc() == null || config.getTokenEnc().isBlank()) {
                throw new IllegalArgumentException("WhatsApp etkinleştirmek için Access Token gerekli");
            }
        }

        config.setUpdatedAt(LocalDateTime.now());
        configRepository.save(config);
        return getSettingsForCurrentSalon();
    }

    public String decryptedToken(SalonWhatsAppConfig config) {
        if (config == null || config.getTokenEnc() == null) {
            return null;
        }
        return secretEncryptionService.decrypt(config.getTokenEnc());
    }

    public boolean isEnabledForCurrentSalon() {
        return getForCurrentSalon().map(SalonWhatsAppConfig::isEnabled).orElse(false);
    }

    public String salonPhoneForCurrentSalon() {
        return getForCurrentSalon().map(SalonWhatsAppConfig::getSalonPhoneE164).orElse(null);
    }
}
