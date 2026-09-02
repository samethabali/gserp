package com.gscrm.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecretEncryptionServiceTest {

    private final SecretEncryptionService service =
            new SecretEncryptionService("test-encryption-key-for-roundtrip", "unused-jwt-secret");

    @Test
    void encryptDecrypt_roundTrip() {
        String plain = "sk-live-0123456789-secret-token";
        String enc = service.encrypt(plain);
        assertThat(enc).startsWith("enc:v1:");
        assertThat(service.decrypt(enc)).isEqualTo(plain);
    }

    @Test
    void decrypt_plainLegacyToken() {
        assertThat(service.decrypt("legacy-plain-token")).isEqualTo("legacy-plain-token");
    }
}
