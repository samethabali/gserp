package com.gscrm.service;

import com.gscrm.config.IyzicoProperties;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class IyzicoWebhookVerifierTest {

    private static final String SECRET = "iyzico-webhook-secret-123";
    private static final String BODY = "{\"organizationId\":5,\"status\":\"SUCCESS\",\"paymentId\":\"p1\"}";

    private IyzicoProperties props(boolean enabled, boolean mock, String webhookSecret) {
        IyzicoProperties p = new IyzicoProperties();
        p.setEnabled(enabled);
        p.setMockMode(mock);
        p.setWebhookSecret(webhookSecret);
        return p;
    }

    private String hmacHex(String secret, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    private String hmacBase64(String secret, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void mockMode_skipsSignatureCheck() {
        IyzicoWebhookVerifier verifier = new IyzicoWebhookVerifier(props(false, true, ""));
        assertThat(verifier.verify(BODY, null)).isTrue();
    }

    @Test
    void enabledProd_rejectsMissingSignature() {
        IyzicoWebhookVerifier verifier = new IyzicoWebhookVerifier(props(true, false, SECRET));
        assertThat(verifier.verify(BODY, null)).isFalse();
        assertThat(verifier.verify(BODY, "  ")).isFalse();
    }

    @Test
    void enabledProd_rejectsWrongSignature() {
        IyzicoWebhookVerifier verifier = new IyzicoWebhookVerifier(props(true, false, SECRET));
        assertThat(verifier.verify(BODY, "deadbeef")).isFalse();
    }

    @Test
    void enabledProd_acceptsValidHexSignature() throws Exception {
        IyzicoWebhookVerifier verifier = new IyzicoWebhookVerifier(props(true, false, SECRET));
        assertThat(verifier.verify(BODY, hmacHex(SECRET, BODY))).isTrue();
    }

    @Test
    void enabledProd_acceptsValidBase64Signature() throws Exception {
        IyzicoWebhookVerifier verifier = new IyzicoWebhookVerifier(props(true, false, SECRET));
        assertThat(verifier.verify(BODY, hmacBase64(SECRET, BODY))).isTrue();
    }

    @Test
    void enabledProd_rejectsWhenSecretMissing() {
        IyzicoWebhookVerifier verifier = new IyzicoWebhookVerifier(props(true, false, ""));
        assertThat(verifier.verify(BODY, "anything")).isFalse();
    }
}
