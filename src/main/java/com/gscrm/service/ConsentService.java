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

    @Transactional
    public Customer findOrCreateCustomerForBooking(String name, String phone, List<String> consentTypes) {
        Long salonId = TenantContext.requireSalonId();
        Customer customer = customerRepository.findBySalonIdAndPhone(salonId, phone)
                .orElseGet(() -> {
                    String[] parts = splitName(name);
                    return customerRepository.save(Customer.builder()
                            .salonId(salonId)
                            .homeSalonId(salonId)
                            .firstName(parts[0])
                            .lastName(parts[1])
                            .phone(phone)
                            .createdAt(LocalDateTime.now())
                            .build());
                });
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

    private String[] splitName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return new String[]{"Müşteri", ""};
        }
        String trimmed = fullName.trim();
        int space = trimmed.indexOf(' ');
        if (space < 0) {
            return new String[]{trimmed, ""};
        }
        return new String[]{trimmed.substring(0, space), trimmed.substring(space + 1).trim()};
    }
}
