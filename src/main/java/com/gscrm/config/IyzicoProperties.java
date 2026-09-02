package com.gscrm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.iyzico")
public class IyzicoProperties {

    private boolean enabled = false;
    private String apiKey = "";
    private String secretKey = "";
    private String baseUrl = "https://api.iyzipay.com";
    /** API anahtarı yokken mock checkout (dev/demo) */
    private boolean mockMode = true;
    private String callbackUrl = "";
    /**
     * Webhook imza doğrulaması için HMAC gizli anahtarı. Tanımsızsa {@link #secretKey}
     * kullanılır. Prod'da gerçek webhook'lar imza taşımalı ve doğrulanmalıdır.
     */
    private String webhookSecret = "";
}
