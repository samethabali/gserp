package com.gscrm.service;

import com.gscrm.config.IyzicoProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

/**
 * iyzico webhook payload'larının kimliğini HMAC-SHA256 imzasıyla doğrular.
 *
 * iyzico, webhook isteklerinde imza header'ı gönderir (ör. {@code X-IYZ-SIGNATURE-V3}).
 * İmza, ham istek gövdesinin webhook gizli anahtarıyla HMAC-SHA256'sıdır. Bu doğrulama
 * yapılmadan gelen payload'a güvenmek, herhangi birinin "SUCCESS" ödeme olayı taklit
 * ederek ücretsiz abonelik aktifleştirmesine izin verir.
 *
 * Karşılaştırma sabit-zamanlı ({@link MessageDigest#isEqual}) yapılır (timing attack'e karşı).
 * Hem hex hem base64 kodlu imzaları kabul eder.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IyzicoWebhookVerifier {

    private static final String HMAC_ALGO = "HmacSHA256";

    private final IyzicoProperties iyzicoProperties;

    /**
     * @return imza geçerliyse true. Mock/disabled modda imza aranmaz (dev/demo).
     */
    public boolean verify(String rawBody, String signatureHeader) {
        // Mock veya kapalı modda gerçek imza beklenmez (dev/demo akışı).
        if (!iyzicoProperties.isEnabled() || iyzicoProperties.isMockMode()) {
            return true;
        }

        String secret = resolveSecret();
        if (secret == null || secret.isBlank()) {
            log.error("iyzico webhook secret tanımsız; imza doğrulanamıyor, payload reddedildi");
            return false;
        }
        if (signatureHeader == null || signatureHeader.isBlank()) {
            log.warn("iyzico webhook imza header'ı yok, payload reddedildi");
            return false;
        }
        if (rawBody == null) {
            rawBody = "";
        }

        byte[] expected = hmac(secret, rawBody);
        if (expected == null) {
            return false;
        }

        String provided = signatureHeader.trim();
        byte[] providedBytes = decodeSignature(provided);
        if (providedBytes == null) {
            log.warn("iyzico webhook imzası çözümlenemedi (hex/base64 değil)");
            return false;
        }
        boolean ok = MessageDigest.isEqual(expected, providedBytes);
        if (!ok) {
            log.warn("iyzico webhook imza uyuşmazlığı");
        }
        return ok;
    }

    private String resolveSecret() {
        String webhookSecret = iyzicoProperties.getWebhookSecret();
        if (webhookSecret != null && !webhookSecret.isBlank()) {
            return webhookSecret;
        }
        return iyzicoProperties.getSecretKey();
    }

    private byte[] hmac(String secret, String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("iyzico webhook HMAC hesaplanamadı: {}", e.getMessage());
            return null;
        }
    }

    private byte[] decodeSignature(String provided) {
        // Önce hex dene, sonra base64.
        try {
            return HexFormat.of().parseHex(provided.toLowerCase());
        } catch (IllegalArgumentException ignored) {
            // hex değil
        }
        try {
            return Base64.getDecoder().decode(provided);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
