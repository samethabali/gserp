package com.gserp.service;

import com.gserp.model.SalonWhatsAppConfig;
import com.gserp.repository.SalonWhatsAppConfigRepository;
import com.gserp.tenant.TenantContext;
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
        if (patch.isEnabled()) config.setEnabled(true);
        if (patch.getTokenEnc() != null) config.setTokenEnc(patch.getTokenEnc());
        if (patch.getPhoneNumberId() != null) config.setPhoneNumberId(patch.getPhoneNumberId());
        if (patch.getBusinessAccountId() != null) config.setBusinessAccountId(patch.getBusinessAccountId());
        if (patch.getSalonPhoneE164() != null) config.setSalonPhoneE164(patch.getSalonPhoneE164());
        if (patch.getWebhookVerifyToken() != null) config.setWebhookVerifyToken(patch.getWebhookVerifyToken());
        config.setUpdatedAt(LocalDateTime.now());
        return configRepository.save(config);
    }

    public boolean isEnabledForCurrentSalon() {
        return getForCurrentSalon().map(SalonWhatsAppConfig::isEnabled).orElse(false);
    }

    public String salonPhoneForCurrentSalon() {
        return getForCurrentSalon().map(SalonWhatsAppConfig::getSalonPhoneE164).orElse(null);
    }
}
