package com.gscrm.service;

import com.gscrm.dto.response.AppointmentResponse;
import com.gscrm.dto.response.RecentCustomerDto;
import com.gscrm.model.Appointment;
import com.gscrm.model.Customer;
import com.gscrm.model.enums.AppointmentStatus;
import com.gscrm.repository.AppointmentRepository;
import com.gscrm.repository.CustomerRepository;
import com.gscrm.repository.PaymentRepository;
import com.gscrm.repository.ProductRepository;
import com.gscrm.repository.ProductSaleRepository;
import com.gscrm.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    private static final Long SALON_ID = 1L;

    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private AppointmentService appointmentService;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductSaleRepository productSaleRepository;
    @Mock
    private ActivityEventService activityEventService;
    @Mock
    private CustomerMatchingService customerMatchingService;

    @InjectMocks
    private CustomerService customerService;

    @BeforeEach
    void setTenant() {
        TenantContext.setSalonId(SALON_ID);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void getRecentCustomers_deduplicatesByPhoneAndUsesLatestAppointment() {
        LocalDateTime newer = LocalDateTime.of(2026, 6, 20, 14, 0);
        LocalDateTime older = LocalDateTime.of(2026, 6, 19, 10, 0);

        Appointment latest = appointment("Merve Aksoy", "05321112233", newer, 10L);
        Appointment duplicatePhone = appointment("Merve A.", "05321112233", older, 11L);
        Appointment other = appointment("Selin Yıldız", "05332223344", newer.minusHours(1), 12L);

        when(appointmentRepository.findBySalonIdOrderByStartTimeDesc(eq(SALON_ID), any(Pageable.class)))
                .thenReturn(List.of(latest, duplicatePhone, other));
        when(appointmentService.toResponse(latest)).thenReturn(
                AppointmentResponse.builder().serviceName("Saç Kesim").staffName("Ayşe").build());
        when(appointmentService.toResponse(other)).thenReturn(
                AppointmentResponse.builder().serviceName("Manikür").staffName("Fatma").build());
        when(customerMatchingService.findByPhone("05321112233"))
                .thenReturn(Optional.of(Customer.builder().id(5L).build()));

        List<RecentCustomerDto> result = customerService.getRecentCustomers(8);

        assertEquals(2, result.size());
        assertEquals("Merve Aksoy", result.get(0).getFullName());
        assertEquals("05321112233", result.get(0).getPhone());
        assertEquals(5L, result.get(0).getId());
        assertEquals("Saç Kesim", result.get(0).getLastServiceName());
        assertEquals("Ayşe", result.get(0).getLastStaffName());
        assertEquals("Selin Yıldız", result.get(1).getFullName());
    }

    @Test
    void getRecentCustomers_skipsBlankPhoneAndCapsLimit() {
        Appointment valid = appointment("Ali", "05551112233", LocalDateTime.now(), 1L);
        Appointment noPhone = appointment("Anonim", "  ", LocalDateTime.now().minusHours(1), 2L);

        when(appointmentRepository.findBySalonIdOrderByStartTimeDesc(eq(SALON_ID), any(Pageable.class)))
                .thenReturn(List.of(valid, noPhone));
        when(appointmentService.toResponse(valid)).thenReturn(
                AppointmentResponse.builder().serviceName("Fön").staffName("Zeynep").build());

        List<RecentCustomerDto> result = customerService.getRecentCustomers(99);

        assertEquals(1, result.size());
        assertNull(result.get(0).getId());
    }

    @Test
    void getRecentCustomers_enforcesMinimumLimitOfOne() {
        when(appointmentRepository.findBySalonIdOrderByStartTimeDesc(eq(SALON_ID), any(Pageable.class)))
                .thenReturn(List.of());

        assertTrue(customerService.getRecentCustomers(0).isEmpty());
    }

    private static Appointment appointment(String name, String phone, LocalDateTime start, long id) {
        return Appointment.builder()
                .id(id)
                .salonId(SALON_ID)
                .customerName(name)
                .customerPhone(phone)
                .staffId(1L)
                .serviceId(1L)
                .startTime(start)
                .endTime(start.plusHours(1))
                .status(AppointmentStatus.COMPLETED)
                .basePrice(BigDecimal.TEN)
                .finalPrice(BigDecimal.TEN)
                .build();
    }
}
