package com.gscrm.dto.request;

import lombok.Data;

@Data
public class WhatsAppSettingsUpdateRequest {
    private Boolean enabled;
    /** Boş bırakılırsa mevcut token korunur */
    private String token;
    private String phoneNumberId;
    private String businessAccountId;
    private String salonPhoneE164;
    private String webhookVerifyToken;
}
