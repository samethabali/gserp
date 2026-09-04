package com.gscrm.service.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Mesajı göndermez, log'a basar — varsayılan sağlayıcı.
 *
 * <p>Geliştirmede doğrulama kodu uygulama konsolundan okunur, böylece akış gerçek
 * bir SMS kredisi olmadan uçtan uca test edilebilir.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "log", matchIfMissing = true)
public class LoggingSmsSender implements SmsSender {

    @Override
    public SmsResult send(String phoneE164, String message, String templateName) {
        log.info("SMS [{}] → {}", phoneE164, message);
        return SmsResult.ok("log-" + UUID.randomUUID());
    }

    @Override
    public String providerName() {
        return "log";
    }
}
