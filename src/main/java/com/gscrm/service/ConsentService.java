package com.gscrm.service;

import com.gscrm.model.ConsentRecord;
import com.gscrm.model.Customer;
import com.gscrm.repository.ConsentRecordRepository;
import com.gscrm.repository.CustomerRepository;
import com.gscrm.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsentService {

    private final ConsentRecordRepository consentRecordRepository;
    private final CustomerRepository customerRepository;
    private final ActivityEventService activityEventService;
    private final CustomerMatchingService customerMatchingService;

    @Transactional
    public void recordBookingConsents(Long customerId, List<String> types) {
        Long salonId = TenantContext.requireSalonId();
        LocalDateTime now = LocalDateTime.now();
        for (String type : types) {
            consentRecordRepository.findFirstByCustomerIdAndSalonIdAndConsentTypeAndRevokedAtIsNull(
                            customerId, salonId, type)
                    .ifPresentOrElse(existing -> {
                    }, () -> consentRecordRepository.save(ConsentRecord.builder()
                            .customerId(customerId)
                            .salonId(salonId)
                            .consentType(type)
                            .version("1.0")
                            .grantedAt(now)
                            .build()));
        }
        customerRepository.findByIdAndSalonId(customerId, salonId).ifPresent(c -> {
            if (c.getConsentAt() == null) {
                c.setConsentAt(now);
                customerRepository.save(c);
            }
        });
        if (customerId != null) {
            activityEventService.record("CONSENT", "CONSENT", customerId, customerId,
                    "Rıza kaydı: " + String.join(", ", types));
        }
    }

    /**
     * Online randevu için müşteriyi bulur/oluşturur ve rızalarını kaydeder.
     *
     * <p>Eşleştirme {@link CustomerMatchingService}'e devredildi: burada ham string
     * eşitliği yapılıyordu ve aynı numaranın farklı yazımları ayrı müşteri
     * oluşturuyordu.
     */
    @Transactional
    public Customer findOrCreateCustomerForBooking(String name, String phone, List<String> consentTypes) {
        Customer customer = customerMatchingService.findOrCreate(name, phone);
        if (consentTypes != null && !consentTypes.isEmpty()) {
            recordBookingConsents(customer.getId(), consentTypes);
        }
        return customer;
    }

    @Transactional
    public void revokeConsent(Long customerId, String consentType) {
        Long salonId = TenantContext.requireSalonId();
        consentRecordRepository.findFirstByCustomerIdAndSalonIdAndConsentTypeAndRevokedAtIsNull(
                        customerId, salonId, consentType)
                .ifPresent(record -> {
                    record.setRevokedAt(LocalDateTime.now());
                    consentRecordRepository.save(record);
                    activityEventService.record("CONSENT", "CONSENT", record.getId(), customerId,
                            "Rıza geri çekildi: " + consentType);
                });
    }

    public List<ConsentRecord> listConsents(Long customerId) {
        return consentRecordRepository.findByCustomerIdAndSalonId(customerId, TenantContext.requireSalonId());
    }

}
