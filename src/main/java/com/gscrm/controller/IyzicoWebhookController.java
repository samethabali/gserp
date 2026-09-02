package com.gscrm.controller;

import com.gscrm.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/webhooks/iyzico")
@RequiredArgsConstructor
public class IyzicoWebhookController {

    private final SubscriptionService subscriptionService;

    @PostMapping
    public ResponseEntity<Void> receive(@RequestBody(required = false) String payload) {
        log.info("Iyzico webhook received ({} bytes)", payload != null ? payload.length() : 0);
        subscriptionService.handleIyzicoWebhook(payload);
        return ResponseEntity.ok().build();
    }
}
