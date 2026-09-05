package com.gscrm.service;

import com.gscrm.model.Appointment;
import com.gscrm.model.Staff;
import com.gscrm.model.WorkingHours;
import com.gscrm.repository.StaffRepository;
import com.gscrm.repository.WorkingHoursRepository;
import com.gscrm.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AvailabilityServiceTest {

    private static final Long SALON_ID = 1L;
    private static final Long STAFF_ID = 7L;
    private static final Long SERVICE_ID = 3L;
    private static final int DURATION_MINUTES = 60;

    /** Testin "bugün"e bağlı olmaması için yeterince ileri, sabit bir Pazartesi. */
    private static final LocalDate MONDAY = LocalDate.of(2030, 6, 17);

    @Mock private WorkingHoursRepository workingHoursRepository;
    @Mock private StaffRepository staffRepository;
    @Mock private SchedulerService schedulerService;
    @Mock private BranchHolidayService branchHolidayService;
    @Mock private BranchPricingService branchPricingService;
    @Mock private SalonSettingsService salonSettingsService;

    @InjectMocks private AvailabilityService availabilityService;

    @BeforeEach
    void setUp() {
        TenantContext.setSalonId(SALON_ID);

        Staff staff = new Staff();
        staff.setId(STAFF_ID);
        staff.setSalonId(SALON_ID);
        staff.setActive(true);
        when(staffRepository.findByIdAndSalonId(STAFF_ID, SALON_ID)).thenReturn(Optional.of(staff));

        when(branchPricingService.effectiveDuration(SERVICE_ID)).thenReturn(DURATION_MINUTES);
        when(branchHolidayService.isHoliday(eq(SALON_ID), any())).thenReturn(false);
        when(schedulerService.busyBlocks(anyLong(), any(), any())).thenReturn(List.of());

        // Ayarlar için varsayılanlar (servis çağrıdaki default değeri geri alır)
        when(salonSettingsService.get(anyString(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(1));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void workingHours(LocalTime start, LocalTime end, boolean dayOff) {
        WorkingHours wh = WorkingHours.builder()
                .id(1L).salonId(SALON_ID).staffId(STAFF_ID)
                .dayOfWeek(MONDAY.getDayOfWeek())
                .startTime(start).endTime(end).dayOff(dayOff)
                .build();
        when(workingHoursRepository.findByStaffIdAndSalonId(STAFF_ID, SALON_ID)).thenReturn(List.of(wh));
    }

    @Test
    void holidayReturnsNoSlots() {
        workingHours(LocalTime.of(9, 0), LocalTime.of(18, 0), false);
        when(branchHolidayService.isHoliday(SALON_ID, MONDAY)).thenReturn(true);

        assertThat(availabilityService.slotsFor(STAFF_ID, SERVICE_ID, MONDAY)).isEmpty();
    }

    @Test
    void dayOffReturnsNoSlots() {
        workingHours(LocalTime.of(9, 0), LocalTime.of(18, 0), true);

        assertThat(availabilityService.slotsFor(STAFF_ID, SERVICE_ID, MONDAY)).isEmpty();
    }

    @Test
    void pastDateReturnsNoSlots() {
        workingHours(LocalTime.of(9, 0), LocalTime.of(18, 0), false);

        assertThat(availabilityService.slotsFor(STAFF_ID, SERVICE_ID, LocalDate.now().minusDays(1))).isEmpty();
    }

    @Test
    void inactiveStaffReturnsNoSlots() {
        workingHours(LocalTime.of(9, 0), LocalTime.of(18, 0), false);
        Staff inactive = new Staff();
        inactive.setId(STAFF_ID);
        inactive.setSalonId(SALON_ID);
        inactive.setActive(false);
        when(staffRepository.findByIdAndSalonId(STAFF_ID, SALON_ID)).thenReturn(Optional.of(inactive));

        assertThat(availabilityService.slotsFor(STAFF_ID, SERVICE_ID, MONDAY)).isEmpty();
    }

    /**
     * Çalışma saati hiç tanımlanmamış olması yeni uzman için normal durum
     * ({@code StaffService.create} satır oluşturmuyor). Bu durumda takvim boş
     * kalmamalı; salon varsayılan penceresi devreye girmeli.
     */
    @Test
    void staffWithoutWorkingHoursFallsBackToSalonDefaultWindow() {
        when(workingHoursRepository.findByStaffIdAndSalonId(STAFF_ID, SALON_ID)).thenReturn(List.of());

        List<AvailabilityService.TimeSlot> slots = availabilityService.slotsFor(STAFF_ID, SERVICE_ID, MONDAY);

        assertThat(slots).isNotEmpty();
        assertThat(slots.get(0).time()).isEqualTo(LocalTime.of(9, 0));
    }

    /** Asıl regresyon: mesai bitişini aşan slot hiç üretilmemeli. */
    @Test
    void neverEmitsSlotWhoseEndExceedsTheWorkingWindow() {
        workingHours(LocalTime.of(9, 0), LocalTime.of(18, 0), false);

        List<AvailabilityService.TimeSlot> slots = availabilityService.slotsFor(STAFF_ID, SERVICE_ID, MONDAY);

        assertThat(slots).isNotEmpty();
        LocalTime last = slots.get(slots.size() - 1).time();
        assertThat(last.plusMinutes(DURATION_MINUTES)).isBeforeOrEqualTo(LocalTime.of(18, 0));
    }

    @Test
    void slotsAreOrderedAndUnique() {
        workingHours(LocalTime.of(9, 0), LocalTime.of(18, 0), false);

        List<LocalTime> times = availabilityService.slotsFor(STAFF_ID, SERVICE_ID, MONDAY)
                .stream().map(AvailabilityService.TimeSlot::time).toList();

        assertThat(times).isSorted().doesNotHaveDuplicates();
    }

    /**
     * Gösterim ile kabulün uyuştuğunun asıl kanıtı: listede müsait görünen her saat
     * için {@code isBookable} da true dönmeli.
     */
    @Test
    void everyAvailableSlotIsAlsoBookable() {
        workingHours(LocalTime.of(9, 0), LocalTime.of(18, 0), false);

        List<AvailabilityService.TimeSlot> slots = availabilityService.slotsFor(STAFF_ID, SERVICE_ID, MONDAY);
        assertThat(slots).isNotEmpty();

        for (AvailabilityService.TimeSlot slot : slots) {
            if (!slot.available()) continue;
            LocalDateTime start = MONDAY.atTime(slot.time());
            assertThat(availabilityService.isBookable(STAFF_ID, SERVICE_ID, start))
                    .as("slot %s müsait görünüyor ama kabul edilmiyor", slot.time())
                    .isTrue();
        }
    }

    @Test
    void slotOutsideTheWindowIsNotBookable() {
        workingHours(LocalTime.of(9, 0), LocalTime.of(18, 0), false);

        assertThat(availabilityService.isBookable(STAFF_ID, SERVICE_ID, MONDAY.atTime(20, 0))).isFalse();
        assertThat(availabilityService.isBookable(STAFF_ID, SERVICE_ID, MONDAY.atTime(8, 0))).isFalse();
    }

    @Test
    void bookedSlotIsMarkedUnavailableAndRejected() {
        workingHours(LocalTime.of(9, 0), LocalTime.of(18, 0), false);
        LocalDateTime taken = MONDAY.atTime(10, 0);
        // Slot basina cakisma sorgusu yerine gunun dolu bloklari tek seferde
        // getiriliyor; test de artik gercek bir randevu koyuyor.
        when(schedulerService.busyBlocks(anyLong(), any(), any()))
                .thenReturn(List.of(Appointment.builder()
                        .id(1L).salonId(SALON_ID).staffId(STAFF_ID)
                        .startTime(taken).endTime(taken.plusMinutes(DURATION_MINUTES))
                        .build()));

        List<AvailabilityService.TimeSlot> slots = availabilityService.slotsFor(STAFF_ID, SERVICE_ID, MONDAY);

        assertThat(slots)
                .filteredOn(s -> s.time().equals(LocalTime.of(10, 0)))
                .singleElement()
                .matches(s -> !s.available());
        assertThat(availabilityService.isBookable(STAFF_ID, SERVICE_ID, taken)).isFalse();
    }
}
