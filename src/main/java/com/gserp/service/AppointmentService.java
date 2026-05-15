package com.gserp.service;

import com.gserp.dto.request.AppointmentCreateRequest;
import com.gserp.dto.request.AppointmentMoveRequest;
import com.gserp.dto.response.AppointmentResponse;
import com.gserp.exception.ConflictException;
import com.gserp.model.*;
import com.gserp.model.enums.*;
import com.gserp.store.MockDataStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final MockDataStore store;
    private final SchedulerService schedulerService;
    private final ResourceLockService resourceLockService;
    private final AuditService auditService;
    private final NotificationService notificationService;

    /**
     * Create appointment(s). If numberOfSessions > 1, creates multiple weekly appointments.
     * Returns the first appointment response. Warnings are logged.
     */
    public AppointmentResponse create(AppointmentCreateRequest req) {
        int sessions = (req.getNumberOfSessions() != null && req.getNumberOfSessions() > 1)
                ? req.getNumberOfSessions() : 1;

        if (sessions == 1) {
            return createSingle(req, null, null, null);
        }

        // Multi-session: generate group ID and create each week
        String groupId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        DayOfWeek preferredDay = req.getPreferredDayOfWeek() != null
                ? req.getPreferredDayOfWeek()
                : req.getStartTime().getDayOfWeek();
        LocalTime preferredTime = req.getStartTime().toLocalTime();

        List<AppointmentResponse> created = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        LocalDate sessionDate = req.getStartTime().toLocalDate();

        for (int i = 1; i <= sessions; i++) {
            // For first session, use the provided date; for subsequent, find next preferred day
            if (i > 1) {
                sessionDate = sessionDate.plusWeeks(1);
                // Adjust to preferred day of week
                while (sessionDate.getDayOfWeek() != preferredDay) {
                    sessionDate = sessionDate.plusDays(1);
                }
            }

            LocalDateTime startTime = sessionDate.atTime(preferredTime);
            AppointmentCreateRequest sessionReq = AppointmentCreateRequest.builder()
                    .customerName(req.getCustomerName())
                    .customerPhone(req.getCustomerPhone())
                    .staffId(req.getStaffId())
                    .serviceId(req.getServiceId())
                    .startTime(startTime)
                    .finalPrice(req.getFinalPrice())
                    .adjustment(req.getAdjustment())
                    .adjustmentNote(req.getAdjustmentNote())
                    .internalNote(req.getInternalNote())
                    .flags(req.getFlags())
                    .build();

            try {
                AppointmentResponse resp = createSingle(sessionReq, groupId, i, sessions);
                created.add(resp);
            } catch (ConflictException e) {
                // Çakışma varsa uyarı olarak ekle ama devam et
                warnings.add("Seans " + i + " (" + sessionDate + "): " + e.getMessage());
                log.warn("Session {} conflict: {}", i, e.getMessage());

                // Yine de oluştur — bankocu halleder
                AppointmentResponse resp = createSingleForced(sessionReq, groupId, i, sessions);
                created.add(resp);
            }
        }

        if (!warnings.isEmpty()) {
            log.info("Session creation warnings: {}", warnings);
        }

        return created.get(0); // İlk seansı dön
    }

    /**
     * Create a single appointment with full conflict validation.
     */
    private AppointmentResponse createSingle(AppointmentCreateRequest req,
                                              String sessionGroupId, Integer sessionNum, Integer totalSessions) {
        ServiceDefinition service = store.findServiceById(req.getServiceId())
                .orElseThrow(() -> new IllegalArgumentException("Hizmet bulunamadı: " + req.getServiceId()));
        LocalDateTime endTime = req.getStartTime().plusMinutes(service.getDurationMinutes());

        Staff staff = store.findStaffById(req.getStaffId())
                .orElseThrow(() -> new IllegalArgumentException("Uzman bulunamadı: " + req.getStaffId()));

        // Check working hours
        if (!schedulerService.isWithinWorkingHours(req.getStaffId(), req.getStartTime(), endTime)) {
            throw new ConflictException(staff.getName() + " bu saatte çalışma saatleri dışında");
        }

        // Check staff availability
        if (!schedulerService.isStaffAvailable(req.getStaffId(), req.getStartTime(), endTime, null)) {
            throw new ConflictException(staff.getName() + " bu saatte başka bir randevusu var");
        }

        // Check & lock resources
        List<Long> lockedResources = resourceLockService.validateAndLock(service, req.getStartTime(), endTime, null);

        return buildAndSave(req, service, endTime, lockedResources, sessionGroupId, sessionNum, totalSessions);
    }

    /**
     * Force-create a single appointment (skip conflict checks — for session warnings).
     */
    private AppointmentResponse createSingleForced(AppointmentCreateRequest req,
                                                     String sessionGroupId, Integer sessionNum, Integer totalSessions) {
        ServiceDefinition service = store.findServiceById(req.getServiceId())
                .orElseThrow(() -> new IllegalArgumentException("Hizmet bulunamadı: " + req.getServiceId()));
        LocalDateTime endTime = req.getStartTime().plusMinutes(service.getDurationMinutes());

        List<Long> lockedResources = new ArrayList<>();
        try {
            lockedResources = resourceLockService.validateAndLock(service, req.getStartTime(), endTime, null);
        } catch (Exception ignored) {}

        return buildAndSave(req, service, endTime, lockedResources, sessionGroupId, sessionNum, totalSessions);
    }

    private AppointmentResponse buildAndSave(AppointmentCreateRequest req, ServiceDefinition service,
                                               LocalDateTime endTime, List<Long> lockedResources,
                                               String sessionGroupId, Integer sessionNum, Integer totalSessions) {
        BigDecimal basePrice = service.getBasePrice();
        BigDecimal adjustment = req.getAdjustment() != null ? req.getAdjustment() : BigDecimal.ZERO;
        BigDecimal finalPrice = req.getFinalPrice() != null ? req.getFinalPrice()
                : basePrice.add(adjustment);

        Appointment appointment = Appointment.builder()
                .customerName(req.getCustomerName())
                .customerPhone(req.getCustomerPhone() != null ? req.getCustomerPhone() : "")
                .staffId(req.getStaffId())
                .serviceId(req.getServiceId())
                .startTime(req.getStartTime())
                .endTime(endTime)
                .status(AppointmentStatus.SCHEDULED)
                .basePrice(basePrice)
                .adjustment(adjustment)
                .adjustmentNote(req.getAdjustmentNote() != null ? req.getAdjustmentNote() : "")
                .finalPrice(finalPrice)
                .internalNote(req.getInternalNote() != null ? req.getInternalNote() : "")
                .sessionGroupId(sessionGroupId)
                .sessionNumber(sessionNum)
                .totalSessions(totalSessions)
                .version(1)
                .resourceIds(new ArrayList<>(lockedResources))
                .flags(new ArrayList<>())
                .build();

        // Add flags
        if (req.getFlags() != null) {
            for (var flagReq : req.getFlags()) {
                appointment.getFlags().add(AppointmentFlag.builder()
                        .id(store.nextFlagId())
                        .flagType(flagReq.getFlagType())
                        .flagValue(flagReq.getFlagValue())
                        .icon(flagReq.getIcon())
                        .build());
            }
        }

        Appointment saved = store.saveAppointment(appointment);

        String sessionInfo = sessionNum != null ? " (Seans " + sessionNum + "/" + totalSessions + ")" : "";
        auditService.log(AuditAction.CREATE, "APPOINTMENT", saved.getId(), null,
                "Yeni randevu: " + req.getCustomerName() + " → " + service.getName() + sessionInfo);

        AppointmentResponse response = toResponse(saved);
        notificationService.broadcastAppointmentChange("CREATE", response);
        notificationService.broadcastDashboardRefresh();

        return response;
    }

    /**
     * Move (drag-drop) an appointment to a new time and/or staff.
     */
    public AppointmentResponse move(AppointmentMoveRequest req) {
        Appointment appointment = store.findAppointmentById(req.getAppointmentId())
                .orElseThrow(() -> new IllegalArgumentException("Randevu bulunamadı: " + req.getAppointmentId()));

        if (appointment.getVersion() != req.getVersion()) {
            throw new ConflictException("Bu randevu başka birisi tarafından güncellendi. Lütfen sayfayı yenileyip tekrar deneyin.");
        }

        ServiceDefinition service = store.findServiceById(appointment.getServiceId())
                .orElseThrow(() -> new IllegalArgumentException("Hizmet bulunamadı"));

        Long newStaffId = req.getNewStaffId() != null ? req.getNewStaffId() : appointment.getStaffId();
        LocalDateTime newEnd = req.getNewStartTime().plusMinutes(service.getDurationMinutes());

        Staff newStaff = store.findStaffById(newStaffId)
                .orElseThrow(() -> new IllegalArgumentException("Uzman bulunamadı"));

        if (!schedulerService.isWithinWorkingHours(newStaffId, req.getNewStartTime(), newEnd)) {
            throw new ConflictException(newStaff.getName() + " bu saatte çalışma saatleri dışında");
        }

        if (!schedulerService.isStaffAvailable(newStaffId, req.getNewStartTime(), newEnd, appointment.getId())) {
            throw new ConflictException(newStaff.getName() + " bu saatte başka bir randevusu var");
        }

        List<Long> lockedResources = resourceLockService.validateAndLock(service, req.getNewStartTime(), newEnd, appointment.getId());

        String oldState = String.format("%s %s-%s",
                store.findStaffById(appointment.getStaffId()).map(Staff::getName).orElse("?"),
                appointment.getStartTime().toLocalTime(), appointment.getEndTime().toLocalTime());

        appointment.setStaffId(newStaffId);
        appointment.setStartTime(req.getNewStartTime());
        appointment.setEndTime(newEnd);
        appointment.setResourceIds(new ArrayList<>(lockedResources));
        appointment.setVersion(appointment.getVersion() + 1);

        store.saveAppointment(appointment);

        String newState = String.format("%s %s-%s",
                newStaff.getName(), appointment.getStartTime().toLocalTime(), appointment.getEndTime().toLocalTime());

        auditService.log(AuditAction.UPDATE, "APPOINTMENT", appointment.getId(), oldState, newState);

        AppointmentResponse response = toResponse(appointment);
        notificationService.broadcastAppointmentChange("MOVE", response);
        notificationService.broadcastDashboardRefresh();

        return response;
    }

    /**
     * Change appointment status with optional reason.
     */
    public AppointmentResponse changeStatus(Long id, AppointmentStatus newStatus, String reason) {
        Appointment appointment = store.findAppointmentById(id)
                .orElseThrow(() -> new IllegalArgumentException("Randevu bulunamadı: " + id));

        String oldStatus = appointment.getStatus().name();
        appointment.setStatus(newStatus);
        if (reason != null && !reason.isBlank()) {
            appointment.setCancellationReason(reason);
        }
        appointment.setVersion(appointment.getVersion() + 1);
        store.saveAppointment(appointment);

        auditService.log(AuditAction.STATUS_CHANGE, "APPOINTMENT", id, oldStatus,
                newStatus.name() + (reason != null ? " — " + reason : ""));

        AppointmentResponse response = toResponse(appointment);
        notificationService.broadcastAppointmentChange("STATUS_CHANGE", response);
        notificationService.broadcastDashboardRefresh();

        return response;
    }

    /**
     * Update appointment details (note, flags, price, customer info).
     */
    public AppointmentResponse update(Long id, AppointmentCreateRequest req) {
        Appointment appointment = store.findAppointmentById(id)
                .orElseThrow(() -> new IllegalArgumentException("Randevu bulunamadı: " + id));

        // Update customer info
        if (req.getCustomerName() != null) appointment.setCustomerName(req.getCustomerName());
        if (req.getCustomerPhone() != null) appointment.setCustomerPhone(req.getCustomerPhone());

        // Update service/staff/time if provided
        if (req.getStaffId() != null) appointment.setStaffId(req.getStaffId());
        if (req.getServiceId() != null) {
            appointment.setServiceId(req.getServiceId());
            ServiceDefinition service = store.findServiceById(req.getServiceId()).orElse(null);
            if (service != null && req.getStartTime() != null) {
                appointment.setEndTime(req.getStartTime().plusMinutes(service.getDurationMinutes()));
                appointment.setBasePrice(service.getBasePrice());
            }
        }
        if (req.getStartTime() != null) {
            appointment.setStartTime(req.getStartTime());
            if (appointment.getServiceId() != null) {
                ServiceDefinition svc = store.findServiceById(appointment.getServiceId()).orElse(null);
                if (svc != null) {
                    appointment.setEndTime(req.getStartTime().plusMinutes(svc.getDurationMinutes()));
                }
            }
        }

        if (req.getInternalNote() != null) appointment.setInternalNote(req.getInternalNote());

        // Pricing
        if (req.getAdjustment() != null) appointment.setAdjustment(req.getAdjustment());
        if (req.getAdjustmentNote() != null) appointment.setAdjustmentNote(req.getAdjustmentNote());
        if (req.getFinalPrice() != null) {
            appointment.setFinalPrice(req.getFinalPrice());
        } else if (req.getAdjustment() != null && appointment.getBasePrice() != null) {
            appointment.setFinalPrice(appointment.getBasePrice().add(req.getAdjustment()));
        }

        // Flags
        if (req.getFlags() != null) {
            appointment.getFlags().clear();
            for (var flagReq : req.getFlags()) {
                appointment.getFlags().add(AppointmentFlag.builder()
                        .id(store.nextFlagId())
                        .appointmentId(id)
                        .flagType(flagReq.getFlagType())
                        .flagValue(flagReq.getFlagValue())
                        .icon(flagReq.getIcon())
                        .build());
            }
        }

        appointment.setVersion(appointment.getVersion() + 1);
        store.saveAppointment(appointment);

        auditService.log(AuditAction.UPDATE, "APPOINTMENT", id, null, "Randevu güncellendi");

        AppointmentResponse response = toResponse(appointment);
        notificationService.broadcastAppointmentChange("UPDATE", response);

        return response;
    }

    public void delete(Long id) {
        Appointment appointment = store.findAppointmentById(id)
                .orElseThrow(() -> new IllegalArgumentException("Randevu bulunamadı: " + id));

        auditService.log(AuditAction.DELETE, "APPOINTMENT", id,
                "Silinen: " + appointment.getCustomerName() + " " + appointment.getStartTime(), null);

        store.deleteAppointment(id);

        AppointmentResponse response = toResponse(appointment);
        notificationService.broadcastAppointmentChange("DELETE", response);
        notificationService.broadcastDashboardRefresh();
    }

    public List<AppointmentResponse> getByDate(LocalDate date) {
        return store.findAppointmentsByDate(date).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<AppointmentResponse> getAll() {
        return store.findAllAppointments().stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Find appointments by customer phone (for history).
     */
    public List<AppointmentResponse> findByCustomerPhone(String phone) {
        return store.findAllAppointments().stream()
                .filter(a -> phone.equals(a.getCustomerPhone()))
                .sorted((a, b) -> b.getStartTime().compareTo(a.getStartTime()))
                .limit(10)
                .map(this::toResponse)
                .toList();
    }

    public AppointmentResponse toResponse(Appointment a) {
        Staff staff = store.findStaffById(a.getStaffId()).orElse(null);
        ServiceDefinition service = store.findServiceById(a.getServiceId()).orElse(null);

        return AppointmentResponse.builder()
                .id(a.getId())
                .customerName(a.getCustomerName())
                .customerPhone(a.getCustomerPhone())
                .staffId(a.getStaffId())
                .staffName(staff != null ? staff.getName() : "?")
                .staffColor(staff != null ? staff.getColorHex() : "#999")
                .serviceId(a.getServiceId())
                .serviceName(service != null ? service.getName() : "?")
                .durationMinutes(service != null ? service.getDurationMinutes() : 0)
                .startTime(a.getStartTime())
                .endTime(a.getEndTime())
                .status(a.getStatus())
                .basePrice(a.getBasePrice())
                .adjustment(a.getAdjustment())
                .adjustmentNote(a.getAdjustmentNote())
                .finalPrice(a.getFinalPrice())
                .internalNote(a.getInternalNote())
                .cancellationReason(a.getCancellationReason())
                .version(a.getVersion())
                .sessionGroupId(a.getSessionGroupId())
                .sessionNumber(a.getSessionNumber())
                .totalSessions(a.getTotalSessions())
                .flags(a.getFlags())
                .resourceIds(a.getResourceIds())
                .build();
    }
}
