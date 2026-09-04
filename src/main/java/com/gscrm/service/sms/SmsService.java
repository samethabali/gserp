package com.gscrm.service.sms;

import com.gscrm.model.SmsLog;
import com.gscrm.repository.SmsLogRepository;
import com.gscrm.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

/**
 * Gönderimi sarar ve her denemenin kaydını tutar.
 *
 * <p>Loglama burada durduğu için sağlayıcı implementasyonları yalnızca gönderim
 * yapar. Kayıt çağıranın işlemine katılır: çağıran geri alınırsa kayıt da geri
 * alınır. Gönderim denemesinin her hâlükârda izlenmesi gerekirse bu metot ayrı bir
 * bean'e taşınıp REQUIRES_NEW ile işaretlenmeli — aynı sınıf içinden çağrıldığında
 * Spring o anotasyonu uygulamaz.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmsService {

    private final SmsSender smsSender;
    private final SmsLogRepository smsLogRepository;

    public SmsSender.SmsResult send(String phoneE164, String message, String templateName) {
        SmsSender.SmsResult result;
        try {
            result = smsSender.send(phoneE164, message, templateName);
        } catch (RuntimeException e) {
            log.error("SMS gönderilemedi: {}", e.getMessage());
            result = SmsSender.SmsResult.failed(e.getMessage());
        }
        record(phoneE164, templateName, result);
        return result;
    }

    private void record(String phoneE164, String templateName, SmsSender.SmsResult result) {
        Long salonId = TenantContext.getSalonId();
        if (salonId == null) return;
        try {
            smsLogRepository.save(SmsLog.builder()
                    .salonId(salonId)
                    .channel("SMS")
                    .templateName(templateName)
                    .recipient(phoneE164)
                    .status(result.success() ? "SENT" : "FAILED")
                    .provider(smsSender.providerName())
                    .providerRef(result.providerRef())
                    .errorMessage(result.errorMessage())
                    .sentAt(LocalDateTime.now())
                    .build());
        } catch (RuntimeException e) {
            // Kayıt tutulamaması gönderimi başarısız saymamalı.
            log.warn("SMS kaydı yazılamadı: {}", e.getMessage());
        }
    }
}
