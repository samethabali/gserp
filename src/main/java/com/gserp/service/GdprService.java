package com.gserp.service;

import com.gserp.model.Customer;
import com.gserp.repository.AppointmentRepository;
import com.gserp.repository.ConsentRecordRepository;
import com.gserp.repository.CustomerRepository;
import com.gserp.repository.PaymentRepository;
import com.gserp.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GdprService {

    private final CustomerRepository customerRepository;
    private final ConsentRecordRepository consentRecordRepository;
    private final AppointmentRepository appointmentRepository;
    private final PaymentRepository paymentRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> exportCustomer(Long customerId) {
        Long salonId = TenantContext.requireSalonId();
        Customer customer = customerRepository.findByIdAndSalonId(customerId, salonId)
                .orElseThrow(() -> new IllegalArgumentException("Müşteri bulunamadı"));

        Map<String, Object> export = new HashMap<>();
        export.put("customer", customer);
        export.put("consents", consentRecordRepository.findByCustomerIdAndSalonId(customerId, salonId));
        if (customer.getPhone() != null) {
            export.put("appointments", appointmentRepository
                    .findBySalonIdAndCustomerPhoneAndStartTimeBeforeOrderByStartTimeDesc(
                            salonId, customer.getPhone(), LocalDateTime.now().plusYears(10)));
            export.put("payments", paymentRepository.findByCustomerPhoneOrderByCollectedAtDesc(customer.getPhone()));
        }
        export.put("exportedAt", LocalDateTime.now());
        return export;
    }

    @Transactional
    public void anonymizeCustomer(Long customerId) {
        Long salonId = TenantContext.requireSalonId();
        Customer customer = customerRepository.findByIdAndSalonId(customerId, salonId)
                .orElseThrow(() -> new IllegalArgumentException("Müşteri bulunamadı"));

        String anonTag = "anon-" + customerId;
        customer.setFirstName("Anonim");
        customer.setLastName(anonTag);
        customer.setPhone(null);
        customer.setEmail(null);
        customer.setNotes("GDPR anonimleştirildi " + LocalDateTime.now());
        customer.setUpdatedAt(LocalDateTime.now());
        customerRepository.save(customer);

        consentRecordRepository.findByCustomerIdAndSalonId(customerId, salonId).forEach(c -> {
            if (c.getRevokedAt() == null) {
                c.setRevokedAt(LocalDateTime.now());
                consentRecordRepository.save(c);
            }
        });
    }
}
