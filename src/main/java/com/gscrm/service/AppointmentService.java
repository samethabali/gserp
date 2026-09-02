package com.gscrm.service;

import com.gscrm.dto.request.AppointmentCreateRequest;
import com.gscrm.dto.request.AppointmentMoveRequest;
import com.gscrm.dto.response.AppointmentResponse;
import com.gscrm.exception.ConflictException;
import com.gscrm.model.*;
import com.gscrm.model.enums.*;
import com.gscrm.service.CampaignService.CouponValidationResult;
import com.gscrm.repository.AppointmentRepository;
import com.gscrm.repository.ServiceDefinitionRepository;
import com.gscrm.repository.StaffRepository;
import com.gscrm.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
@Transactional(readOnly = true)
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final ServiceDefinitionRepository serviceRepository;
    private final StaffRepository staffRepository;
    private final SchedulerService schedulerService;
    private final ResourceLockService resourceLockService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final com.gscrm.notification.whatsapp.WhatsAppNotificationService whatsAppNotificationService;

    /**
     * Create appointment(s). If numberOfSessions > 1, creates multiple weekly appointments.
     * Returns the first appointment response. Warnings are logged.
     */
    @Transactional
    public AppointmentResponse create(AppointmentCreateRequest req) {
        int sessions = (req.getNumberOfSessions() != null && req.getNumberOfSessions() > 1)
                ? req.getNumberOfSessions() : 1;

        if (sessions == 1) {
            return createSingle(req, null, null, null);
        }

        String groupId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        DayOfWeek preferredDay = req.getPreferredDayOfWeek() != null
                ? req.getPreferredDayOfWeek()
                : req.getStartTime().getDayOfWeek();
        LocalTime preferredTime = req.getStartTime().toLocalTime();

        List<AppointmentResponse> created = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        LocalDate sessionDate = req.getStartTime().toLocalDate();

        for (int i = 1; i <= sessions; i++) {
            if (i > 1) {
                sessionDate = sessionDate.plusWeeks(1);
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
                warnings.add("Seans " + i + " (" + sessionDate + "): " + e.getMessage());
                log.warn("Session {} conflict: {}", i, e.getMessage());

                AppointmentResponse resp = createSingleForced(sessionReq, groupId, i, sessions);
                created.add(resp);
            }
        }

        if (!warnings.isEmpty()) {
            log.info("Session creation warnings: {}", warnings);
        }

        return created.get(0);
    }

    private AppointmentResponse createSingle(AppointmentCreateRequest req,
                                              String sessionGroupId, Integer sessionNum, Integer totalSessions) {
        Long salonId = TenantContext.requireSalonId();
        ServiceDefinition service = serviceRepository.findByIdAndSalonId(req.getServiceId(), salonId)
                .orElseThrow(() -> new IllegalArgumentException("Hizmet bulunamadı: " + req.getServiceId()));
        LocalDateTime endTime = req.getStartTime().plusMinutes(service.getDurationMinutes());

        Staff staff = staffRepository.findByIdAndSalonId(req.getStaffId(), salonId)
                .orElseThrow(() -> new IllegalArgumentException("Uzman bulunamadı: " + req.getStaffId()));

        if (!schedulerService.isWithinWorkingHours(req.getStaffId(), req.getStartTime(), endTime)) {
            throw new ConflictException(staff.getName() + " bu saatte çalışma saatleri dışında");
        }

        if (!schedulerService.isStaffAvailable(req.getStaffId(), req.getStartTime(), endTime, null)) {
            throw new ConflictException(staff.getName() + " bu saatte başka bir randevusu var");
        }

        List<Long> lockedResources = resourceLockService.validateAndLock(service, req.getStartTime(), endTime, null);

        return buildAndSave(req, service, endTime, lockedResources, sessionGroupId, sessionNum, totalSessions);
    }

    private AppointmentResponse createSingleForced(AppointmentCreateRequest req,
                                                     String sessionGroupId, Integer sessionNum, Integer totalSessions) {
        Long salonId = TenantContext.requireSalonId();
        ServiceDefinition service = serviceRepository.findByIdAndSalonId(req.getServiceId(), salonId)
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

        LocalDateTime now = LocalDateTime.now();
        Long salonId = TenantContext.requireSalonId();
        Appointment appointment = Appointment.builder()
                .salonId(salonId)
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
                .createdAt(now)
                .updatedAt(now)
                .resourceIds(new ArrayList<>(lockedResources))
                .flags(new ArrayList<>())
                .build();

        if (req.getFlags() != null) {
            for (var flagReq : req.getFlags()) {
                AppointmentFlag flag = AppointmentFlag.builder()
                        .appointment(appointment)
                        .flagType(flagReq.getFlagType())
                        .flagValue(flagReq.getFlagValue())
                        .icon(flagReq.getIcon())
                        .build();
                appointment.getFlags().add(flag);
            }
        }

        Appointment saved = appointmentRepository.save(appointment);

        String sessionInfo = sessionNum != null ? " (Seans " + sessionNum + "/" + totalSessions + ")" : "";
        auditService.log(AuditAction.CREATE, "APPOINTMENT", saved.getId(), null,
                "Yeni randevu: " + req.getCustomerName() + " → " + service.getName() + sessionInfo);

        AppointmentResponse response = toResponse(saved);
        notificationService.broadcastAppointmentChange("CREATE", response);
        notificationService.broadcastDashboardRefresh();

        return response;
    }

    /**
     * Müşteri portalından gelen randevu isteği — PENDING_APPROVAL statüsüyle oluşturur.
     */
    @Transactional
    public AppointmentResponse createRequest(AppointmentCreateRequest req) {
        return createRequest(req, null, BigDecimal.ZERO);
    }

    /**
     * Kupon veya sadakat indirimiyle randevu isteği oluşturur.
     */
    @Transactional
    public AppointmentResponse createRequest(AppointmentCreateRequest req,
                                             CouponValidationResult coupon,
                                             BigDecimal loyaltyDiscountPct) {
        Long salonId = TenantContext.requireSalonId();
        ServiceDefinition service = serviceRepository.findByIdAndSalonId(req.getServiceId(), salonId)
                .orElseThrow(() -> new IllegalArgumentException("Hizmet bulunamadı: " + req.getServiceId()));
        LocalDateTime endTime = req.getStartTime().plusMinutes(service.getDurationMinutes());

        Staff staff = staffRepository.findByIdAndSalonId(req.getStaffId(), salonId)
                .orElseThrow(() -> new IllegalArgumentException("Uzman bulunamadı: " + req.getStaffId()));

        if (!schedulerService.isWithinWorkingHours(req.getStaffId(), req.getStartTime(), endTime)) {
            throw new ConflictException(staff.getName() + " bu saatte çalışma saatleri dışında");
        }
        if (!schedulerService.isStaffAvailable(req.getStaffId(), req.getStartTime(), endTime, null)) {
            throw new ConflictException(staff.getName() + " bu saatte müsait değil");
        }

        BigDecimal basePrice = service.getBasePrice();
        BigDecimal finalPrice;
        BigDecimal adjustment;
        String adjustmentNote = req.getAdjustmentNote() != null ? req.getAdjustmentNote() : "";

        if (coupon != null) {
            // Kupon indirimi
            if (coupon.discountType() == com.gscrm.model.enums.DiscountType.PERCENTAGE) {
                BigDecimal disc = basePrice.multiply(coupon.discountValue())
                        .divide(java.math.BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
                finalPrice = basePrice.subtract(disc).max(BigDecimal.ZERO);
            } else {
                finalPrice = basePrice.subtract(coupon.discountValue()).max(BigDecimal.ZERO);
            }
            adjustment = finalPrice.subtract(basePrice);
            adjustmentNote = "Kupon: " + coupon.code();
        } else if (loyaltyDiscountPct != null && loyaltyDiscountPct.compareTo(BigDecimal.ZERO) > 0) {
            // Sadakat indirimi
            BigDecimal disc = basePrice.multiply(loyaltyDiscountPct)
                    .divide(java.math.BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
            finalPrice = basePrice.subtract(disc).max(BigDecimal.ZERO);
            adjustment = finalPrice.subtract(basePrice);
        } else {
            adjustment = BigDecimal.ZERO;
            finalPrice = basePrice;
        }

        LocalDateTime now = LocalDateTime.now();
        Appointment appointment = Appointment.builder()
                .salonId(salonId)
                .customerName(req.getCustomerName())
                .customerPhone(req.getCustomerPhone() != null ? req.getCustomerPhone() : "")
                .staffId(req.getStaffId())
                .serviceId(req.getServiceId())
                .startTime(req.getStartTime())
                .endTime(endTime)
                .status(AppointmentStatus.PENDING_APPROVAL)
                .basePrice(basePrice)
                .adjustment(adjustment)
                .adjustmentNote(adjustmentNote)
                .finalPrice(finalPrice)
                .internalNote(req.getInternalNote() != null ? req.getInternalNote() : "")
                .createdAt(now)
                .updatedAt(now)
                .resourceIds(new ArrayList<>())
                .flags(new ArrayList<>())
                .build();

        Appointment saved = appointmentRepository.save(appointment);

        auditService.log(AuditAction.CREATE, "APPOINTMENT", saved.getId(), null,
                "Randevu isteği: " + req.getCustomerName() + " → " + service.getName() + " (onay bekliyor)");

        AppointmentResponse response = toResponse(saved);
        notificationService.broadcastAppointmentChange("CREATE", response);
        notificationService.broadcastDashboardRefresh();
        whatsAppNotificationService.onRequestReceived(response);

        return response;
    }

    /**
     * Move (drag-drop) an appointment to a new time and/or staff.
     */
    @Transactional
    public AppointmentResponse move(AppointmentMoveRequest req) {
        Long salonId = TenantContext.requireSalonId();
        Appointment appointment = appointmentRepository.findByIdAndSalonId(req.getAppointmentId(), salonId)
                .orElseThrow(() -> new IllegalArgumentException("Randevu bulunamadı: " + req.getAppointmentId()));

        if (appointment.getVersion() != req.getVersion()) {
            throw new ConflictException("Bu randevu başka birisi tarafından güncellendi. Lütfen sayfayı yenileyip tekrar deneyin.");
        }

        ServiceDefinition service = serviceRepository.findByIdAndSalonId(appointment.getServiceId(), salonId)
                .orElseThrow(() -> new IllegalArgumentException("Hizmet bulunamadı"));

        Long newStaffId = req.getNewStaffId() != null ? req.getNewStaffId() : appointment.getStaffId();
        LocalDateTime newEnd = req.getNewStartTime().plusMinutes(service.getDurationMinutes());

        Staff newStaff = staffRepository.findByIdAndSalonId(newStaffId, salonId)
                .orElseThrow(() -> new IllegalArgumentException("Uzman bulunamadı"));

        if (!schedulerService.isWithinWorkingHours(newStaffId, req.getNewStartTime(), newEnd)) {
            throw new ConflictException(newStaff.getName() + " bu saatte çalışma saatleri dışında");
        }

        if (!schedulerService.isStaffAvailable(newStaffId, req.getNewStartTime(), newEnd, appointment.getId())) {
            throw new ConflictException(newStaff.getName() + " bu saatte başka bir randevusu var");
        }

        List<Long> lockedResources = resourceLockService.validateAndLock(service, req.getNewStartTime(), newEnd, appointment.getId());

        String oldState = String.format("%s %s-%s",
                staffRepository.findByIdAndSalonId(appointment.getStaffId(), salonId).map(Staff::getName).orElse("?"),
                appointment.getStartTime().toLocalTime(), appointment.getEndTime().toLocalTime());

        appointment.setStaffId(newStaffId);
        appointment.setStartTime(req.getNewStartTime());
        appointment.setEndTime(newEnd);
        appointment.getResourceIds().clear();
        appointment.getResourceIds().addAll(lockedResources);
        appointment.setUpdatedAt(LocalDateTime.now());

        Appointment saved = appointmentRepository.save(appointment);

        String newState = String.format("%s %s-%s",
                newStaff.getName(), saved.getStartTime().toLocalTime(), saved.getEndTime().toLocalTime());

        auditService.log(AuditAction.UPDATE, "APPOINTMENT", saved.getId(), oldState, newState);

        AppointmentResponse response = toResponse(saved);
        notificationService.broadcastAppointmentChange("MOVE", response);
        notificationService.broadcastDashboardRefresh();

        return response;
    }

    /**
     * Change appointment status with optional reason.
     */
    @Transactional
    public AppointmentResponse changeStatus(Long id, AppointmentStatus newStatus, String reason) {
        Long salonId = TenantContext.requireSalonId();
        Appointment appointment = appointmentRepository.findByIdAndSalonId(id, salonId)
                .orElseThrow(() -> new IllegalArgumentException("Randevu bulunamadı: " + id));

        String oldStatus = appointment.getStatus().name();
        appointment.setStatus(newStatus);
        if (reason != null && !reason.isBlank()) {
            appointment.setCancellationReason(reason);
        }
        appointment.setUpdatedAt(LocalDateTime.now());
        Appointment saved = appointmentRepository.save(appointment);

        auditService.log(AuditAction.STATUS_CHANGE, "APPOINTMENT", id, oldStatus,
                newStatus.name() + (reason != null ? " — " + reason : ""));

        AppointmentResponse response = toResponse(saved);
        notificationService.broadcastAppointmentChange("STATUS_CHANGE", response);
        notificationService.broadcastDashboardRefresh();

        if (newStatus == AppointmentStatus.SCHEDULED && AppointmentStatus.valueOf(oldStatus) == AppointmentStatus.PENDING_APPROVAL) {
            whatsAppNotificationService.onApproved(response);
        } else if (newStatus == AppointmentStatus.CANCELLED) {
            whatsAppNotificationService.onCancelled(response, reason);
        }

        return response;
    }

    /**
     * Update appointment details (note, flags, price, customer info).
     */
    @Transactional
    public AppointmentResponse update(Long id, AppointmentCreateRequest req) {
        Long salonId = TenantContext.requireSalonId();
        Appointment appointment = appointmentRepository.findByIdAndSalonId(id, salonId)
                .orElseThrow(() -> new IllegalArgumentException("Randevu bulunamadı: " + id));

        if (req.getCustomerName() != null) appointment.setCustomerName(req.getCustomerName());
        if (req.getCustomerPhone() != null) appointment.setCustomerPhone(req.getCustomerPhone());

        if (req.getStaffId() != null) appointment.setStaffId(req.getStaffId());
        if (req.getServiceId() != null) {
            appointment.setServiceId(req.getServiceId());
            ServiceDefinition service = serviceRepository.findByIdAndSalonId(req.getServiceId(), salonId).orElse(null);
            if (service != null && req.getStartTime() != null) {
                appointment.setEndTime(req.getStartTime().plusMinutes(service.getDurationMinutes()));
                appointment.setBasePrice(service.getBasePrice());
            }
        }
        if (req.getStartTime() != null) {
            appointment.setStartTime(req.getStartTime());
            if (appointment.getServiceId() != null) {
                ServiceDefinition svc = serviceRepository.findByIdAndSalonId(appointment.getServiceId(), salonId).orElse(null);
                if (svc != null) {
                    appointment.setEndTime(req.getStartTime().plusMinutes(svc.getDurationMinutes()));
                }
            }
        }

        if (req.getInternalNote() != null) appointment.setInternalNote(req.getInternalNote());

        if (req.getAdjustment() != null) appointment.setAdjustment(req.getAdjustment());
        if (req.getAdjustmentNote() != null) appointment.setAdjustmentNote(req.getAdjustmentNote());
        if (req.getFinalPrice() != null) {
            appointment.setFinalPrice(req.getFinalPrice());
        } else if (req.getAdjustment() != null && appointment.getBasePrice() != null) {
            appointment.setFinalPrice(appointment.getBasePrice().add(req.getAdjustment()));
        }

        if (req.getFlags() != null) {
            appointment.getFlags().clear();
            for (var flagReq : req.getFlags()) {
                AppointmentFlag flag = AppointmentFlag.builder()
                        .appointment(appointment)
                        .flagType(flagReq.getFlagType())
                        .flagValue(flagReq.getFlagValue())
                        .icon(flagReq.getIcon())
                        .build();
                appointment.getFlags().add(flag);
            }
        }

        appointment.setUpdatedAt(LocalDateTime.now());
        Appointment saved = appointmentRepository.save(appointment);

        auditService.log(AuditAction.UPDATE, "APPOINTMENT", id, null, "Randevu güncellendi");

        AppointmentResponse response = toResponse(saved);
        notificationService.broadcastAppointmentChange("UPDATE", response);

        return response;
    }

    @Transactional
    public void delete(Long id) {
        Long salonId = TenantContext.requireSalonId();
        Appointment appointment = appointmentRepository.findByIdAndSalonId(id, salonId)
                .orElseThrow(() -> new IllegalArgumentException("Randevu bulunamadı: " + id));

        auditService.log(AuditAction.DELETE, "APPOINTMENT", id,
                "Silinen: " + appointment.getCustomerName() + " " + appointment.getStartTime(), null);

        AppointmentResponse response = toResponse(appointment);
        appointmentRepository.delete(appointment);

        notificationService.broadcastAppointmentChange("DELETE", response);
        notificationService.broadcastDashboardRefresh();
    }

    public Appointment getEntity(Long id) {
        return appointmentRepository.findByIdAndSalonId(id, TenantContext.requireSalonId())
                .orElseThrow(() -> new IllegalArgumentException("Randevu bulunamadı: " + id));
    }

    public List<AppointmentResponse> getByDate(LocalDate date) {
        return getByDate(date, null);
    }

    public List<AppointmentResponse> getByDate(LocalDate date, Long staffIdFilter) {
        Long salonId = TenantContext.requireSalonId();
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();
        List<Appointment> appointments = staffIdFilter != null
                ? appointmentRepository.findBySalonIdAndStaffIdAndStartTimeBetween(salonId, staffIdFilter, start, end)
                : appointmentRepository.findBySalonIdAndStartTimeBetween(salonId, start, end);
        return appointments.stream()
                .sorted((a, b) -> a.getStartTime().compareTo(b.getStartTime()))
                .map(this::toResponse)
                .toList();
    }

    public List<AppointmentResponse> getAll() {
        Long salonId = TenantContext.requireSalonId();
        return appointmentRepository.findBySalonId(salonId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Find appointments by customer phone (for history).
     */
    public List<AppointmentResponse> findByCustomerPhone(String phone) {
        Long salonId = TenantContext.requireSalonId();
        return appointmentRepository.findBySalonIdAndCustomerPhoneOrderByStartTimeDesc(salonId, phone).stream()
                .limit(10)
                .map(this::toResponse)
                .toList();
    }

    public AppointmentResponse toResponse(Appointment a) {
        Long salonId = a.getSalonId() != null ? a.getSalonId() : TenantContext.getSalonId();
        Staff staff = salonId != null
                ? staffRepository.findByIdAndSalonId(a.getStaffId(), salonId).orElse(null)
                : staffRepository.findById(a.getStaffId()).orElse(null);
        ServiceDefinition service = salonId != null
                ? serviceRepository.findByIdAndSalonId(a.getServiceId(), salonId).orElse(null)
                : serviceRepository.findById(a.getServiceId()).orElse(null);

        return AppointmentResponse.builder()
                .id(a.getId())
                .salonId(a.getSalonId())
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
