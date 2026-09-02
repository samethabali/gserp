package com.gscrm.service;

import com.gscrm.dto.response.AppointmentResponse;
import com.gscrm.dto.response.CustomerDetailResponse;
import com.gscrm.dto.response.CustomerResponse;
import com.gscrm.dto.response.PaymentResponse;
import com.gscrm.dto.response.RecentCustomerDto;
import com.gscrm.model.Appointment;
import com.gscrm.model.Customer;
import com.gscrm.repository.AppointmentRepository;
import com.gscrm.repository.CustomerRepository;
import com.gscrm.repository.PaymentRepository;
import com.gscrm.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.gscrm.dto.response.ProductSaleResponse;
import com.gscrm.model.Product;
import com.gscrm.model.ProductSale;
import com.gscrm.repository.ProductRepository;
import com.gscrm.repository.ProductSaleRepository;

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
        Long salonId = TenantContext.requireSalonId();
        List<Customer> customers;
        if (query != null && !query.isBlank()) {
            customers = customerRepository.searchBySalonIdAndQuery(salonId, query.trim());
        } else {
            customers = customerRepository.findBySalonId(salonId);
        }
        return customers.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public Optional<CustomerDetailResponse> getDetail(Long id) {
        Long salonId = TenantContext.requireSalonId();
        return customerRepository.findByIdAndSalonId(id, salonId).map(c -> {
            LocalDateTime now = LocalDateTime.now();

            List<AppointmentResponse> past = appointmentRepository
                    .findBySalonIdAndCustomerPhoneAndStartTimeBeforeOrderByStartTimeDesc(salonId, c.getPhone(), now)
                    .stream().map(appointmentService::toResponse).collect(Collectors.toList());

            List<AppointmentResponse> upcoming = appointmentRepository
                    .findBySalonIdAndCustomerPhoneAndStartTimeAfterOrderByStartTimeAsc(salonId, c.getPhone(), now)
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
        Long salonId = TenantContext.requireSalonId();
        customer.setSalonId(salonId);
        customer.setCreatedAt(LocalDateTime.now());
        customer.setUpdatedAt(LocalDateTime.now());
        if (customer.getBalance() == null) customer.setBalance(BigDecimal.ZERO);
        return customerRepository.save(customer);
    }

    @Transactional
    public Customer update(Long id, Customer updated) {
        Long salonId = TenantContext.requireSalonId();
        Customer existing = customerRepository.findByIdAndSalonId(id, salonId)
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
        Long salonId = TenantContext.requireSalonId();
        return customerRepository.findBySalonIdAndPhone(salonId, phone).map(this::toResponse);
    }

    public List<RecentCustomerDto> getRecentCustomers(int limit) {
        Long salonId = TenantContext.requireSalonId();
        int cappedLimit = Math.min(Math.max(limit, 1), 20);
        List<Appointment> recent = appointmentRepository.findBySalonIdOrderByStartTimeDesc(
                salonId, PageRequest.of(0, 50));

        Map<String, RecentCustomerDto> seen = new LinkedHashMap<>();
        for (Appointment appointment : recent) {
            String phone = appointment.getCustomerPhone();
            if (phone == null || phone.isBlank() || seen.containsKey(phone)) {
                continue;
            }

            AppointmentResponse response = appointmentService.toResponse(appointment);
            Long customerId = customerRepository.findBySalonIdAndPhone(salonId, phone)
                    .map(Customer::getId)
                    .orElse(null);

            seen.put(phone, RecentCustomerDto.builder()
                    .id(customerId)
                    .fullName(appointment.getCustomerName())
                    .phone(phone)
                    .lastVisit(appointment.getStartTime())
                    .lastServiceName(response.getServiceName())
                    .lastStaffName(response.getStaffName())
                    .build());

            if (seen.size() >= cappedLimit) {
                break;
            }
        }
        return new ArrayList<>(seen.values());
    }

    private CustomerResponse toResponse(Customer c) {
        Long salonId = TenantContext.requireSalonId();
        String phone = c.getPhone() != null ? c.getPhone() : "";
        int total = (int) appointmentRepository.countBySalonIdAndCustomerPhone(salonId, phone);
        int upcoming = (int) appointmentRepository.countBySalonIdAndCustomerPhoneAndStartTimeAfter(
                salonId, phone, LocalDateTime.now());

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
