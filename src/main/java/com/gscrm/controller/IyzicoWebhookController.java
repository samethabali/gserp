package com.gscrm.controller;

import com.gscrm.service.IyzicoCheckoutService;
import com.gscrm.service.IyzicoWebhookVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/webhooks/iyzico")
@RequiredArgsConstructor
public class IyzicoWebhookController {

    private static final String SIGNATURE_HEADER = "X-IYZ-SIGNATURE-V3";

    private final IyzicoCheckoutService iyzicoCheckoutService;
    private final IyzicoWebhookVerifier webhookVerifier;

    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestBody(required = false) String payload,
            @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature) {
        log.info("Iyzico webhook received ({} bytes)", payload != null ? payload.length() : 0);

        if (!webhookVerifier.verify(payload, signature)) {
            log.warn("Iyzico webhook imza doğrulaması başarısız, istek reddedildi");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        iyzicoCheckoutService.handleWebhookPayload(payload);
        return ResponseEntity.ok().build();
    }
}
