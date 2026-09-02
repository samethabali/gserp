package com.gscrm.notification.whatsapp;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.whatsapp")
public class WhatsAppProperties {
    private boolean enabled = false;
    private String apiUrl = "https://graph.facebook.com/v21.0";
    private String token = "";
    private String phoneNumberId = "";
    private String businessAccountId = "";
    private String salonPhoneE164 = "";
    private String webhookVerifyToken = "";
}
