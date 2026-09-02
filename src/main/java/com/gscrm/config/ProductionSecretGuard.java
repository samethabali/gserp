package com.gscrm.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Prod profilinde güvenli olmayan varsayılan sırların kullanılmasını engeller.
 *
 * Repo'daki dev JWT secret'ı (application.yml varsayılanı) prod'a sızarsa, saldırgan
 * geçerli token üretebilir. Bu guard, prod'da bilinen dev secret veya çok kısa/zayıf
 * secret tespit ederse uygulamayı başlangıçta durdurur (fail-fast).
 */
@Slf4j
@Component
@Profile("prod")
public class ProductionSecretGuard {

    /** application.yml içindeki dev varsayılanı — prod'da ASLA kullanılmamalı. */
    private static final String KNOWN_DEV_JWT_SECRET =
            "ZGV2LW9ubHktc2VjcmV0LWNoYW5nZS1tZS1kZXYtb25seS1zZWNyZXQtY2hhbmdlLW1l";

    private static final int MIN_SECRET_LENGTH = 32;

    private final String jwtSecret;

    public ProductionSecretGuard(@Value("${app.jwt.secret}") String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void validate() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            fail("JWT secret (JWT_SECRET) tanımsız. Prod'da zorunludur.");
        }
        if (KNOWN_DEV_JWT_SECRET.equals(jwtSecret)) {
            fail("Prod'da dev JWT secret'ı kullanılıyor. JWT_SECRET env değişkenini güçlü, "
                    + "rastgele bir base64 değer ile tanımlayın.");
        }
        if (jwtSecret.length() < MIN_SECRET_LENGTH) {
            fail("JWT secret çok kısa (< " + MIN_SECRET_LENGTH + " karakter). "
                    + "En az 256-bit base64 secret kullanın.");
        }
        log.info("Prod secret guard: JWT secret doğrulaması geçti.");
    }

    private void fail(String message) {
        throw new IllegalStateException("[GÜVENLİK] " + message);
    }
}
