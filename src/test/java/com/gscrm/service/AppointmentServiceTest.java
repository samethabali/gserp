package com.gscrm.service;

import com.gscrm.dto.request.AppointmentCreateRequest;
import com.gscrm.dto.response.AppointmentResponse;
import com.gscrm.model.Appointment;
import com.gscrm.model.ServiceDefinition;
import com.gscrm.model.Staff;
import com.gscrm.model.enums.AppointmentStatus;
import com.gscrm.notification.whatsapp.WhatsAppNotificationService;
import com.gscrm.repository.AppointmentRepository;
import com.gscrm.repository.ServiceDefinitionRepository;
import com.gscrm.repository.StaffRepository;
import com.gscrm.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    private static final Long SALON_ID = 1L;

    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private ServiceDefinitionRepository serviceRepository;
    @Mock
    private StaffRepository staffRepository;
    @Mock
    private SchedulerService schedulerService;
    @Mock
    private ResourceLockService resourceLockService;
    @Mock
    private AuditService auditService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private WhatsAppNotificationService whatsAppNotificationService;

    @InjectMocks
    private AppointmentService appointmentService;

    @BeforeEach
    void setTenant() {
        TenantContext.setSalonId(SALON_ID);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void createRequest_setsPendingApprovalStatus() {
        ServiceDefinition service = ServiceDefinition.builder()
                .id(1L)
                .salonId(SALON_ID)
                .name("Manikür")
                .durationMinutes(60)
                .basePrice(BigDecimal.valueOf(500))
                .build();
        Staff staff = Staff.builder().id(2L).salonId(SALON_ID).name("Zeynep").build();

        when(serviceRepository.findByIdAndSalonId(1L, SALON_ID)).thenReturn(Optional.of(service));
        when(staffRepository.findByIdAndSalonId(2L, SALON_ID)).thenReturn(Optional.of(staff));
        when(schedulerService.isWithinWorkingHours(eq(2L), any(), any())).thenReturn(true);
        when(schedulerService.isStaffAvailable(eq(2L), any(), any(), isNull())).thenReturn(true);
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> {
            Appointment a = inv.getArgument(0);
            a.setId(100L);
            return a;
        });

        AppointmentCreateRequest req = AppointmentCreateRequest.builder()
                .customerName("Ayşe Yılmaz")
                .customerPhone("05551234567")
                .staffId(2L)
                .serviceId(1L)
                .startTime(LocalDateTime.of(2026, 6, 20, 10, 0))
                .build();

        AppointmentResponse response = appointmentService.createRequest(req);

        ArgumentCaptor<Appointment> captor = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepository).save(captor.capture());
        assertEquals(AppointmentStatus.PENDING_APPROVAL, captor.getValue().getStatus());
        assertEquals(SALON_ID, captor.getValue().getSalonId());
        assertNotNull(response);
        assertEquals(100L, response.getId());
    }
}
