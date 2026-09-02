package com.gscrm.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionSecretGuardTest {

    private static final String JWT = "jwt-secret-that-is-longer-than-thirty-two-characters";
    private static final String ENCRYPTION = "encryption-key-independent-and-longer-than-thirty-two";

    @Test
    void rejectsMissingOrCoupledEncryptionKey() {
        assertThrows(IllegalStateException.class,
                () -> guard(JWT, "").validate());
        assertThrows(IllegalStateException.class,
                () -> guard(JWT, JWT).validate());
    }

    @Test
    void acceptsIndependentSecrets() {
        assertDoesNotThrow(() -> guard(JWT, ENCRYPTION).validate());
    }

    private ProductionSecretGuard guard(String jwt, String encryption) {
        return new ProductionSecretGuard(jwt, encryption);
    }
}
