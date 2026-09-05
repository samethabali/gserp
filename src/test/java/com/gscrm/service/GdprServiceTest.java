package com.gscrm.service;

import com.gscrm.model.Customer;
import com.gscrm.repository.AppointmentRepository;
import com.gscrm.repository.ConsentRecordRepository;
import com.gscrm.repository.CustomerRepository;
import com.gscrm.repository.PaymentRepository;
import com.gscrm.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GdprServiceTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private ConsentRecordRepository consentRecordRepository;
    @Mock private AppointmentRepository appointmentRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private ActivityEventService activityEventService;

    @InjectMocks
    private GdprService gdprService;

    @BeforeEach
    void setUp() {
        TenantContext.setSalonId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void anonymizeCustomerRecordsActivity() {
        Customer customer = Customer.builder()
                .id(99L)
                .salonId(1L)
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .phone("123456789")
                .build();
                
        when(customerRepository.findByIdAndSalonId(99L, 1L)).thenReturn(Optional.of(customer));
        when(consentRecordRepository.findByCustomerIdAndSalonId(99L, 1L)).thenReturn(java.util.Collections.emptyList());

        gdprService.anonymizeCustomer(99L);

        verify(customerRepository).save(any(Customer.class));
        verify(activityEventService).record(
                eq("GDPR_ANONYMIZE"),
                eq("CUSTOMER"),
                eq(99L),
                eq(99L),
                eq("Müşteri kalıcı olarak anonimleştirildi (KVKK/GDPR)")
        );
        
        assertThat(customer.getFirstName()).isEqualTo("Anonim");
        assertThat(customer.getLastName()).isEqualTo("anon-99");
        assertThat(customer.getEmail()).isNull();
        assertThat(customer.getPhone()).isNull();
    }
}
