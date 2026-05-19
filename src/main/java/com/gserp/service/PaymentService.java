package com.gserp.service;

import com.gserp.dto.request.PaymentCreateRequest;
import com.gserp.dto.response.DailyPaymentSummary;
import com.gserp.dto.response.PaymentResponse;
import com.gserp.model.Appointment;
import com.gserp.model.Customer;
import com.gserp.model.Payment;
import com.gserp.model.enums.PaymentMethod;
import com.gserp.model.enums.PaymentStatus;
import com.gserp.repository.AppointmentRepository;
import com.gserp.repository.CustomerRepository;
import com.gserp.repository.PaymentRepository;
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
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final AppointmentRepository appointmentRepository;
    private final CustomerRepository customerRepository;

    @Transactional
    public PaymentResponse collect(PaymentCreateRequest req) {
        Long appointmentId = req.getAppointmentId();
        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("Randevu bulunamadı: " + appointmentId));

        Payment payment = Payment.builder()
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

        return toResponse(saved);
    }

    private void updateCustomerBalance(String phone, BigDecimal finalPrice, BigDecimal paid, PaymentStatus status) {
        Optional<Customer> opt = customerRepository.findByPhone(phone);
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
