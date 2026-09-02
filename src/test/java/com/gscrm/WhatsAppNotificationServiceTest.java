package com.gscrm;

import com.gscrm.notification.whatsapp.WhatsAppNotificationService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WhatsAppNotificationServiceTest {

    @Test
    void normalizePhone_trMobile() {
        assertEquals("+905321112233", WhatsAppNotificationService.normalizePhone("0532 111 22 33"));
        assertEquals("+905321112233", WhatsAppNotificationService.normalizePhone("5321112233"));
        assertNull(WhatsAppNotificationService.normalizePhone(""));
        assertNull(WhatsAppNotificationService.normalizePhone("invalid"));
    }
}
