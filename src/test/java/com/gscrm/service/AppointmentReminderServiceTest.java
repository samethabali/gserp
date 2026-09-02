package com.gscrm.service;

import com.gscrm.model.Appointment;
import com.gscrm.model.Salon;
import com.gscrm.model.enums.AppointmentStatus;
import com.gscrm.repository.AppointmentRepository;
import com.gscrm.repository.SalonRepository;
import com.gscrm.repository.ServiceDefinitionRepository;
import com.gscrm.repository.StaffRepository;
import com.gscrm.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppointmentReminderServiceTest {

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void oneNotificationFailureDoesNotStopRemainingReminders() {
        AppointmentRepository appointments = mock(AppointmentRepository.class);
        SalonRepository salons = mock(SalonRepository.class);
        StaffRepository staff = mock(StaffRepository.class);
        ServiceDefinitionRepository services = mock(ServiceDefinitionRepository.class);
        NotificationService notifications = mock(NotificationService.class);

        Salon salon = Salon.builder().id(7L).organizationId(3L).active(true).build();
        Appointment first = Appointment.builder().id(11L).salonId(7L)
                .staffId(21L).serviceId(31L).customerName("Bir").build();
        Appointment second = Appointment.builder().id(12L).salonId(7L)
                .staffId(22L).serviceId(32L).customerName("Iki").build();

        when(salons.findAll()).thenReturn(List.of(salon));
        when(appointments.findBySalonIdAndStartTimeBetweenAndStatusIn(
                eq(7L), any(), any(), eq(List.of(
                        AppointmentStatus.SCHEDULED, AppointmentStatus.IN_PROGRESS))))
                .thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("temporary failure"))
                .doNothing()
                .when(notifications).broadcastNotificationForSalon(
                        eq(7L), eq("APPOINTMENT_REMINDER"), any(), any());

        new AppointmentReminderService(appointments, salons, staff, services, notifications)
                .sendDailyReminders();

        verify(notifications, times(2)).broadcastNotificationForSalon(
                eq(7L), eq("APPOINTMENT_REMINDER"), any(), any());
        assertNull(TenantContext.getSalonId());
    }
}
