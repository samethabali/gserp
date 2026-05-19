package com.gserp.service;

import com.gserp.dto.response.AppointmentResponse;
import com.gserp.dto.response.CustomerDetailResponse;
import com.gserp.dto.response.CustomerResponse;
import com.gserp.dto.response.PaymentResponse;
import com.gserp.model.Customer;
import com.gserp.repository.AppointmentRepository;
import com.gserp.repository.CustomerRepository;
import com.gserp.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.gserp.dto.response.ProductSaleResponse;
import com.gserp.model.Product;
import com.gserp.model.ProductSale;
import com.gserp.repository.ProductRepository;
import com.gserp.repository.ProductSaleRepository;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final AppointmentRepository appointmentRepository;
    private final PaymentRepository paymentRepository;
    private final AppointmentService appointmentService;
    private final ProductRepository productRepository;
    private final ProductSaleRepository productSaleRepository;

    public List<CustomerResponse> getAll(String query) {
        List<Customer> customers;
        if (query != null && !query.isBlank()) {
            customers = customerRepository.searchByQuery(query.trim());
        } else {
            customers = customerRepository.findAll();
        }
        return customers.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public Optional<CustomerDetailResponse> getDetail(Long id) {
        return customerRepository.findById(id).map(c -> {
            LocalDateTime now = LocalDateTime.now();

            List<AppointmentResponse> past = appointmentRepository
                    .findByCustomerPhoneAndStartTimeBeforeOrderByStartTimeDesc(c.getPhone(), now)
                    .stream().map(appointmentService::toResponse).collect(Collectors.toList());

            List<AppointmentResponse> upcoming = appointmentRepository
                    .findByCustomerPhoneAndStartTimeAfterOrderByStartTimeAsc(c.getPhone(), now)
                    .stream().map(appointmentService::toResponse).collect(Collectors.toList());

            List<PaymentResponse> payments = c.getPhone() != null
                    ? paymentRepository.findByCustomerPhoneOrderByCollectedAtDesc(c.getPhone())
                        .stream().map(p -> PaymentResponse.builder()
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
                                .build())
                        .collect(Collectors.toList())
                    : List.of();

            Map<Long, String> productNames = productRepository.findAll().stream()
                    .collect(Collectors.toMap(Product::getId, Product::getName));

            List<ProductSaleResponse> sales = productSaleRepository.findByCustomerIdOrderBySoldAtDesc(c.getId())
                    .stream().map(s -> ProductSaleResponse.builder()
                            .id(s.getId())
                            .productId(s.getProductId())
                            .productName(productNames.getOrDefault(s.getProductId(), "Bilinmeyen Ürün (ID: " + s.getProductId() + ")"))
                            .quantity(s.getQuantity())
                            .unitPrice(s.getUnitPrice())
                            .totalPrice(s.getTotalPrice())
                            .soldAt(s.getSoldAt())
                            .build())
                    .collect(Collectors.toList());

            return CustomerDetailResponse.builder()
                    .id(c.getId())
                    .firstName(c.getFirstName())
                    .lastName(c.getLastName())
                    .fullName(c.getFirstName() + " " + (c.getLastName() != null ? c.getLastName() : ""))
                    .phone(c.getPhone())
                    .email(c.getEmail())
                    .notes(c.getNotes())
                    .balance(c.getBalance() != null ? c.getBalance() : BigDecimal.ZERO)
                    .createdAt(c.getCreatedAt())
                    .pastAppointments(past)
                    .upcomingAppointments(upcoming)
                    .payments(payments)
                    .productSales(sales)
                    .build();
        });
    }

    @Transactional
    public Customer create(Customer customer) {
        customer.setCreatedAt(LocalDateTime.now());
        customer.setUpdatedAt(LocalDateTime.now());
        if (customer.getBalance() == null) customer.setBalance(BigDecimal.ZERO);
        return customerRepository.save(customer);
    }

    @Transactional
    public Customer update(Long id, Customer updated) {
        Customer existing = customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Müşteri bulunamadı: " + id));
        existing.setFirstName(updated.getFirstName());
        existing.setLastName(updated.getLastName());
        existing.setPhone(updated.getPhone());
        existing.setEmail(updated.getEmail());
        existing.setNotes(updated.getNotes());
        existing.setUpdatedAt(LocalDateTime.now());
        return customerRepository.save(existing);
    }

    public Optional<CustomerResponse> lookupByPhone(String phone) {
        return customerRepository.findByPhone(phone).map(this::toResponse);
    }

    private CustomerResponse toResponse(Customer c) {
        String phone = c.getPhone() != null ? c.getPhone() : "";
        int total = (int) appointmentRepository.countByCustomerPhone(phone);
        int upcoming = (int) appointmentRepository.countByCustomerPhoneAndStartTimeAfter(phone, LocalDateTime.now());

        return CustomerResponse.builder()
                .id(c.getId())
                .firstName(c.getFirstName())
                .lastName(c.getLastName())
                .fullName(c.getFirstName() + " " + (c.getLastName() != null ? c.getLastName() : ""))
                .phone(c.getPhone())
                .email(c.getEmail())
                .notes(c.getNotes())
                .balance(c.getBalance() != null ? c.getBalance() : BigDecimal.ZERO)
                .totalAppointments(total)
                .upcomingAppointments(upcoming)
                .createdAt(c.getCreatedAt())
                .build();
    }
}
