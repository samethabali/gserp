package com.gscrm.service;

import com.gscrm.dto.request.AppointmentCreateRequest;
import com.gscrm.exception.ConflictException;
import com.gscrm.dto.response.AppointmentResponse;
import com.gscrm.model.Appointment;
import com.gscrm.model.ServiceDefinition;
import com.gscrm.model.Staff;
import com.gscrm.model.enums.AppointmentStatus;
import com.gscrm.model.enums.BodyRegion;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
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
    private BranchHolidayService branchHolidayService;
    @Mock
    private BranchPricingService branchPricingService;
    @Mock
    private ActivityEventService activityEventService;
    @Mock
    private AvailabilityService availabilityService;
    @Mock
    private SalonSettingsService salonSettingsService;
    @Mock
    private VerificationCodeService verificationCodeService;

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
        when(staffRepository.lockByIdAndSalonId(2L, SALON_ID)).thenReturn(Optional.of(staff));
        when(branchHolidayService.isHoliday(eq(SALON_ID), any())).thenReturn(false);
        when(branchPricingService.effectiveDuration(1L)).thenReturn(60);
        when(branchPricingService.effectivePrice(1L)).thenReturn(BigDecimal.valueOf(500));
        // Online istek müsaitliği artık yalnızca slot listesini üreten servisten
        // sorulur; SchedulerService'in ikili kontrolü bu yolda kullanılmıyor.
        when(availabilityService.isBookable(eq(2L), eq(1L), any())).thenReturn(true);
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
                .startTime(LocalDateTime.now().plusDays(7).withHour(10).withMinute(0).withSecond(0).withNano(0))
                .build();

        AppointmentResponse response = appointmentService.createRequest(req);

        ArgumentCaptor<Appointment> captor = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepository).save(captor.capture());
        assertEquals(AppointmentStatus.PENDING_APPROVAL, captor.getValue().getStatus());
        assertEquals(SALON_ID, captor.getValue().getSalonId());
        assertNotNull(response);
        assertEquals(100L, response.getId());
    }

    /**
     * Çalışma saati kontrolü geçmiş tarihleri yakalamaz — geçmişteki bir salı 11:00
     * de mesai içindedir — bu yüzden ayrı bir kontrol gerekiyor.
     */
    @Test
    void createRequest_rejectsPastDate() {
        ServiceDefinition service = ServiceDefinition.builder()
                .id(1L)
                .salonId(SALON_ID)
                .name("Manikür")
                .durationMinutes(60)
                .basePrice(BigDecimal.valueOf(500))
                .build();

        when(serviceRepository.findByIdAndSalonId(1L, SALON_ID)).thenReturn(Optional.of(service));

        AppointmentCreateRequest req = AppointmentCreateRequest.builder()
                .customerName("Ayşe Yılmaz")
                .customerPhone("05551234567")
                .staffId(2L)
                .serviceId(1L)
                .startTime(LocalDateTime.now().minusDays(3))
                .build();

        ConflictException ex = assertThrows(ConflictException.class,
                () -> appointmentService.createRequest(req));
        assertEquals("Geçmiş bir tarih için randevu alınamaz", ex.getMessage());
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    /**
     * Epilasyon bölgeleri online istekle birlikte kaydedilmeli; yanıt da bunları
     * enum sırasında (baştan ayağa) döndürmeli — panelde ve müşteri ekranında
     * liste her açılışta aynı okunsun diye.
     */
    @Test
    void createRequest_persistsBodyRegionsInAnatomicalOrder() {
        ServiceDefinition service = ServiceDefinition.builder()
                .id(1L)
                .salonId(SALON_ID)
                .name("Lazer Epilasyon")
                .durationMinutes(30)
                .basePrice(BigDecimal.valueOf(750))
                .build();
        Staff staff = Staff.builder().id(2L).salonId(SALON_ID).name("Zeynep").build();

        when(serviceRepository.findByIdAndSalonId(1L, SALON_ID)).thenReturn(Optional.of(service));
        when(staffRepository.lockByIdAndSalonId(2L, SALON_ID)).thenReturn(Optional.of(staff));
        when(branchHolidayService.isHoliday(eq(SALON_ID), any())).thenReturn(false);
        when(branchPricingService.effectiveDuration(1L)).thenReturn(30);
        when(branchPricingService.effectivePrice(1L)).thenReturn(BigDecimal.valueOf(750));
        when(availabilityService.isBookable(eq(2L), eq(1L), any())).thenReturn(true);
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> {
            Appointment a = inv.getArgument(0);
            a.setId(101L);
            return a;
        });

        AppointmentCreateRequest req = AppointmentCreateRequest.builder()
                .customerName("Ayşe Yılmaz")
                .customerPhone("05551234567")
                .staffId(2L)
                .serviceId(1L)
                .startTime(LocalDateTime.now().plusDays(3).withHour(11).withMinute(0).withSecond(0).withNano(0))
                // Bilerek karışık sırada ve yinelenmiş gönderiliyor.
                .bodyRegions(List.of(BodyRegion.BIKINI, BodyRegion.UNDERARM, BodyRegion.BIKINI))
                .build();

        AppointmentResponse response = appointmentService.createRequest(req);

        ArgumentCaptor<Appointment> captor = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepository).save(captor.capture());
        assertEquals(2, captor.getValue().getBodyRegions().size());
        assertEquals(List.of(BodyRegion.UNDERARM, BodyRegion.BIKINI), response.getBodyRegions());
    }
}
