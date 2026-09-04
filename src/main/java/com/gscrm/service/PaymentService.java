package com.gscrm.service;

import com.gscrm.dto.request.PaymentCreateRequest;
import com.gscrm.dto.response.DailyPaymentSummary;
import com.gscrm.dto.response.PaymentResponse;
import com.gscrm.model.Appointment;
import com.gscrm.model.Customer;
import com.gscrm.model.Payment;
import com.gscrm.model.enums.PaymentMethod;
import com.gscrm.model.enums.PaymentStatus;
import com.gscrm.repository.AppointmentRepository;
import com.gscrm.repository.CustomerRepository;
import com.gscrm.repository.PaymentRepository;
import com.gscrm.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final AppointmentRepository appointmentRepository;
    private final CustomerRepository customerRepository;
    private final ActivityEventService activityEventService;
    private final CustomerMatchingService customerMatchingService;

    @Transactional
    public PaymentResponse collect(PaymentCreateRequest req) {
        Long appointmentId = req.getAppointmentId();
        Long salonId = TenantContext.requireSalonId();
        Appointment appt = appointmentRepository.findByIdAndSalonId(appointmentId, salonId)
                .orElseThrow(() -> new IllegalArgumentException("Randevu bulunamadı: " + appointmentId));

        Payment payment = Payment.builder()
                .salonId(salonId)
                .appointmentId(req.getAppointmentId())
                .customerName(appt.getCustomerName())
                .customerPhone(appt.getCustomerPhone())
                .amount(req.getAmount())
                .method(req.getMethod())
                .status(req.getStatus() != null ? req.getStatus() : PaymentStatus.PAID)
                .deferredNote(req.getDeferredNote())
                .collectedAt(LocalDateTime.now())
                .build();

        Payment saved = paymentRepository.saveAndFlush(payment);

        // Telefon üzerinden müşteri kaydıyla eşleştir ve bakiyeyi güncelle
        if (appt.getCustomerPhone() != null && !appt.getCustomerPhone().isBlank()) {
            updateCustomerBalance(appt.getCustomerPhone(), appt.getFinalPrice(), req.getAmount(), req.getStatus());
        }
        activityEventService.recordForCustomerPhone("PAYMENT", "PAYMENT", saved.getId(),
                appt.getCustomerPhone(), "Tahsilat: " + saved.getAmount());

        return toResponse(saved);
    }

    private void updateCustomerBalance(String phone, BigDecimal finalPrice, BigDecimal paid, PaymentStatus status) {
        // Normalize eşleştirme şart: online randevu artık farklı yazılmış numarayı
        // mevcut müşteriye bağlıyor, ham lookup burada sessizce boş dönerdi.
        Optional<Customer> opt = customerMatchingService.findByPhone(phone);
        if (opt.isEmpty()) return;

        Customer customer = opt.get();
        BigDecimal currentBalance = customer.getBalance() != null ? customer.getBalance() : BigDecimal.ZERO;

        if (status == PaymentStatus.DEFERRED) {
            // Ödeme ertelenmiş: müşteri borcu artar (negatif bakiye)
            BigDecimal debt = finalPrice != null ? finalPrice : paid;
            customer.setBalance(currentBalance.subtract(debt));
        } else {
            // Ödeme yapılmış: fazla ödeme varsa kredi olarak eklenir
            BigDecimal expectedAmount = finalPrice != null ? finalPrice : paid;
            BigDecimal diff = paid.subtract(expectedAmount);
            customer.setBalance(currentBalance.add(diff));
        }

        customerRepository.save(customer);
    }

    public List<PaymentResponse> getByDate(LocalDate date) {
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to   = date.plusDays(1).atStartOfDay();
        return paymentRepository.findByCollectedAtBetweenOrderByCollectedAtAsc(from, to)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public DailyPaymentSummary getSummary(LocalDate date) {
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to   = date.plusDays(1).atStartOfDay();

        BigDecimal cash     = paymentRepository.sumByMethodAndRange(from, to, PaymentMethod.CASH);
        BigDecimal card     = paymentRepository.sumByMethodAndRange(from, to, PaymentMethod.CARD);
        List<Payment> all   = paymentRepository.findByCollectedAtBetweenOrderByCollectedAtAsc(from, to);
        BigDecimal deferred = all.stream()
                .filter(p -> p.getStatus() == PaymentStatus.DEFERRED)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return DailyPaymentSummary.builder()
                .date(date)
                .cashTotal(cash)
                .cardTotal(card)
                .deferredTotal(deferred)
                .grandTotal(cash.add(card))
                .paymentCount(all.size())
                .build();
    }

    public List<PaymentResponse> getByCustomerPhone(String phone) {
        return paymentRepository.findByCustomerPhoneOrderByCollectedAtDesc(phone)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public Optional<PaymentResponse> getByAppointmentId(Long appointmentId) {
        return paymentRepository.findByAppointmentId(appointmentId).map(this::toResponse);
    }

    private PaymentResponse toResponse(Payment p) {
        return PaymentResponse.builder()
                .id(p.getId())
                .appointmentId(p.getAppointmentId())
                .customerName(p.getCustomerName())
                .customerPhone(p.getCustomerPhone())
                .amount(p.getAmount())
                .method(p.getMethod())
                .status(p.getStatus())
                .deferredNote(p.getDeferredNote())
                .collectedAt(p.getCollectedAt())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
