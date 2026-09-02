package com.gscrm.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class WhatsAppSettingsResponse {
    private boolean enabled;
    private boolean tokenConfigured;
    private boolean globalFallbackEnabled;
    private String phoneNumberId;
    private String businessAccountId;
    private String salonPhoneE164;
    private String webhookVerifyToken;
    private LocalDateTime updatedAt;
}
