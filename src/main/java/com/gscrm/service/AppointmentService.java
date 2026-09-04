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
import com.gscrm.util.PhoneNormalizer;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AppointmentService {

    /** Aynı numaradan aynı anda onay bekleyebilecek istek sayısı. */
    private static final int DEFAULT_MAX_PENDING_PER_PHONE = 2;

    private final AppointmentRepository appointmentRepository;
    private final ServiceDefinitionRepository serviceRepository;
    private final StaffRepository staffRepository;
    private final SchedulerService schedulerService;
    private final AvailabilityService availabilityService;
    private final ResourceLockService resourceLockService;
    private final BranchPricingService branchPricingService;
    private final BranchHolidayService branchHolidayService;
    private final AuditService auditService;
    private final ActivityEventService activityEventService;
    private final NotificationService notificationService;
    private final SalonSettingsService salonSettingsService;
    private final VerificationCodeService verificationCodeService;

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
                    .bodyRegions(req.getBodyRegions())
                    .build();

            try {
                AppointmentResponse resp = createSingle(sessionReq, groupId, i, sessions);
                created.add(resp);
            } catch (ConflictException e) {
                // Çakışan seans atlanır. Eskiden kayıt zorla yazılıyordu; bu, uzmanı
                // aynı saatte iki müşteriye veriyordu ve V29'daki çakışma kısıtıyla
                // zaten mümkün değil. Seri, sığan seanslarla oluşturulur.
                warnings.add("Seans " + i + " (" + sessionDate + "): " + e.getMessage());
                log.warn("Seans {} çakıştı, atlandı: {}", i, e.getMessage());
            }
        }

        if (!warnings.isEmpty()) {
            log.info("Seans oluşturma uyarıları: {}", warnings);
        }

        if (created.isEmpty()) {
            throw new ConflictException("Seçilen saat için hiçbir seans oluşturulamadı: "
                    + String.join("; ", warnings));
        }

        return created.get(0);
    }

    private AppointmentResponse createSingle(AppointmentCreateRequest req,
                                              String sessionGroupId, Integer sessionNum, Integer totalSessions) {
        Long salonId = TenantContext.requireSalonId();
        ServiceDefinition service = serviceRepository.findByIdAndSalonId(req.getServiceId(), salonId)
                .orElseThrow(() -> new IllegalArgumentException("Hizmet bulunamadı: " + req.getServiceId()));
        if (branchHolidayService.isHoliday(salonId, req.getStartTime().toLocalDate())) {
            throw new ConflictException("Salon bu tarihte kapalı (şube tatili)");
        }
        int durationMinutes = branchPricingService.effectiveDuration(req.getServiceId());
        LocalDateTime endTime = req.getStartTime().plusMinutes(durationMinutes);

        Staff staff = staffRepository.lockByIdAndSalonId(req.getStaffId(), salonId)
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

    private AppointmentResponse buildAndSave(AppointmentCreateRequest req, ServiceDefinition service,
                                               LocalDateTime endTime, List<Long> lockedResources,
                                               String sessionGroupId, Integer sessionNum, Integer totalSessions) {
        BigDecimal basePrice = branchPricingService.effectivePrice(service.getId());
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
                .bodyRegions(toRegionSet(req.getBodyRegions()))
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
        activityEventService.recordForCustomerPhone("CREATE", "APPOINTMENT", saved.getId(),
                saved.getCustomerPhone(), "Randevu oluşturuldu: " + req.getCustomerName());

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
        // Online istek yalnızca ileri bir tarihe verilebilir. Çalışma saati kontrolü
        // geçmiş tarihleri yakalamaz: geçmişteki bir salı 11:00 de mesai içindedir.
        // Panelden geçmişe kayıt (yürüyerek gelen müşteri) bilinçli olarak serbest.
        if (req.getStartTime().isBefore(LocalDateTime.now())) {
            throw new ConflictException("Geçmiş bir tarih için randevu alınamaz");
        }
        if (branchHolidayService.isHoliday(salonId, req.getStartTime().toLocalDate())) {
            throw new ConflictException("Salon bu tarihte kapalı (şube tatili)");
        }
        requireVerifiedPhone(req);
        guardPendingRequestCap(salonId, req.getCustomerPhone());

        int durationMinutes = branchPricingService.effectiveDuration(req.getServiceId());
        LocalDateTime endTime = req.getStartTime().plusMinutes(durationMinutes);

        // Uzman satırını kilitle: aynı uzmana gelen eşzamanlı istekler burada sıraya
        // girer, böylece aşağıdaki müsaitlik kontrolü bir öncekinin kaydını görür.
        Staff staff = staffRepository.lockByIdAndSalonId(req.getStaffId(), salonId)
                .orElseThrow(() -> new IllegalArgumentException("Uzman bulunamadı: " + req.getStaffId()));

        // Online istekte müsaitlik kontrolü, slot listesini üreten servisin ta kendisidir:
        // arayüzün gösterdiği saat ile burada kabul edilen saat böylece hiç ayrışmaz.
        if (!availabilityService.isBookable(req.getStaffId(), req.getServiceId(), req.getStartTime())) {
            throw new ConflictException(staff.getName() + " için seçilen saat artık müsait değil");
        }

        BigDecimal basePrice = branchPricingService.effectivePrice(service.getId());
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
                .bodyRegions(toRegionSet(req.getBodyRegions()))
                .flags(new ArrayList<>())
                .build();

        Appointment saved = appointmentRepository.save(appointment);

        auditService.log(AuditAction.CREATE, "APPOINTMENT", saved.getId(), null,
                "Randevu isteği: " + req.getCustomerName() + " → " + service.getName() + " (onay bekliyor)");
        activityEventService.recordForCustomerPhone("BOOKING", "APPOINTMENT", saved.getId(),
                saved.getCustomerPhone(), "Online randevu isteği: " + req.getCustomerName());

        AppointmentResponse response = toResponse(saved);
        notificationService.broadcastAppointmentChange("CREATE", response);
        notificationService.broadcastDashboardRefresh();

        return response;
    }

    /**
     * SMS doğrulama açıksa, isteğin doğrulanmış bir numaraya ait olmasını şart koşar.
     *
     * <p>Bayrak kapalıyken bu metot hiçbir şey yapmaz ve token yok sayılır — akış
     * bugünkü hâliyle aynı kalır. Açıkken token tek kullanımlık olarak harcanır ve
     * içindeki numara ile formdaki numaranın aynı olması aranır; aksi hâlde kendi
     * numarasını doğrulayan biri başkasının numarasıyla randevu yazdırabilirdi.
     */
    private void requireVerifiedPhone(AppointmentCreateRequest req) {
        if (!verificationCodeService.isEnabled()) return;

        String verifiedPhone = verificationCodeService.consume(req.getVerificationToken()).orElse(null);
        if (verifiedPhone == null) {
            throw new ConflictException("Telefon doğrulaması gerekiyor. Lütfen numaranıza gelen kodu girin.");
        }
        if (!verifiedPhone.equals(PhoneNormalizer.normalizeOrNull(req.getCustomerPhone()))) {
            // Fırlatınca işlem geri alınır ve token'ın "harcandı" damgası da geri alınır:
            // uyuşmayan istek token'ı yakmaz, yalnızca başarılı bir randevu yakar.
            // Bu davranış bilinçli — aksi hâlde tek bir hatalı deneme müşteriyi yeniden
            // kod istemeye mecbur bırakırdı.
            throw new ConflictException("Doğrulanan numara ile randevu numarası aynı değil");
        }
    }

    /**
     * Aynı numaradan onay bekleyen istek sayısına tavan koyar.
     *
     * <p>Doğrulama kapalıyken tek gerçek fren salon sahibinin onayı; bu tavan onun
     * kuyruğunu tek bir numaranın doldurmasını engeller. Telefon çözümlenemiyorsa
     * ham metin üzerinden sayılır — aksi hâlde çöp numara yazmak bariz bir bypass olurdu.
     * Müşteri portalı da bu metottan geçtiği için o taraf da kapsanır.
     */
    private void guardPendingRequestCap(Long salonId, String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) return;

        int cap = DEFAULT_MAX_PENDING_PER_PHONE;
        try {
            String configured = salonSettingsService.get(
                    "booking.max_pending_per_phone", String.valueOf(DEFAULT_MAX_PENDING_PER_PHONE));
            if (configured != null && !configured.isBlank()) {
                cap = Integer.parseInt(configured.trim());
            }
        } catch (NumberFormatException e) {
            log.warn("Geçersiz booking.max_pending_per_phone ayarı, varsayılan {} kullanılıyor",
                    DEFAULT_MAX_PENDING_PER_PHONE);
        }
        if (cap <= 0) return;

        String normalized = PhoneNormalizer.normalizeOrNull(rawPhone);
        long pending = normalized != null
                ? appointmentRepository.countBySalonIdAndCustomerPhoneNormalizedAndStatus(
                        salonId, normalized, AppointmentStatus.PENDING_APPROVAL)
                : appointmentRepository.countBySalonIdAndCustomerPhoneAndStatus(
                        salonId, rawPhone, AppointmentStatus.PENDING_APPROVAL);

        if (pending >= cap) {
            throw new ConflictException("Onay bekleyen " + pending + " randevu isteğiniz var. "
                    + "Salon bunları yanıtlayana kadar yeni istek oluşturamazsınız.");
        }
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

        Staff newStaff = staffRepository.lockByIdAndSalonId(newStaffId, salonId)
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
        activityEventService.recordForCustomerPhone("UPDATE", "APPOINTMENT", saved.getId(),
                saved.getCustomerPhone(), "Randevu taşındı");

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
        activityEventService.recordForCustomerPhone("STATUS_CHANGE", "APPOINTMENT", id,
                saved.getCustomerPhone(), "Randevu durumu: " + oldStatus + " → " + newStatus.name());

        AppointmentResponse response = toResponse(saved);
        notificationService.broadcastAppointmentChange("STATUS_CHANGE", response);
        notificationService.broadcastDashboardRefresh();

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

        // null = "dokunma", boş liste = "hepsini kaldır". Bölge seçicisi görünmeyen
        // bir hizmete geçildiğinde arayüz boş liste gönderir ve eski bölgeler silinir.
        if (req.getBodyRegions() != null) {
            appointment.getBodyRegions().clear();
            appointment.getBodyRegions().addAll(toRegionSet(req.getBodyRegions()));
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
        activityEventService.recordForCustomerPhone("UPDATE", "APPOINTMENT", id,
                saved.getCustomerPhone(), "Randevu güncellendi");

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
        activityEventService.recordForCustomerPhone("DELETE", "APPOINTMENT", id,
                appointment.getCustomerPhone(), "Randevu silindi: " + appointment.getCustomerName());

        AppointmentResponse response = toResponse(appointment);
        appointmentRepository.delete(appointment);

        notificationService.broadcastAppointmentChange("DELETE", response);
        notificationService.broadcastDashboardRefresh();
    }

    public Appointment getEntity(Long id) {
        return appointmentRepository.findByIdAndSalonId(id, TenantContext.requireSalonId())
                .orElseThrow(() -> new IllegalArgumentException("Randevu bulunamadı: " + id));
    }

    /** Erişim kontrolü için salon filtresi olmadan yükler (yanlış şube → 403). */
    public Appointment findEntityById(Long id) {
        return appointmentRepository.findById(id)
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
        String normalized = PhoneNormalizer.normalizeOrNull(phone);
        if (normalized == null) return List.of();
        return appointmentRepository
                .findBySalonIdAndCustomerPhoneNormalizedOrderByStartTimeDesc(salonId, normalized).stream()
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
                .bodyRegions(sortedRegions(a.getBodyRegions()))
                .flags(a.getFlags())
                .resourceIds(a.getResourceIds())
                .build();
    }

    /** Yinelenenleri ve {@code null}'ları ayıklar; sıra istekteki sırayı korur. */
    private static Set<BodyRegion> toRegionSet(List<BodyRegion> regions) {
        Set<BodyRegion> result = new LinkedHashSet<>();
        if (regions != null) {
            regions.stream().filter(java.util.Objects::nonNull).forEach(result::add);
        }
        return result;
    }

    /**
     * Bölgeleri enum sırasına (baştan ayağa) döker.
     *
     * <p>Kümenin veritabanından dönüş sırası garanti değil; sabit bir sıra olmadan
     * aynı randevunun bölge listesi her açılışta farklı dizilebilirdi.
     */
    private static List<BodyRegion> sortedRegions(Set<BodyRegion> regions) {
        if (regions == null || regions.isEmpty()) return List.of();
        return regions.stream().sorted().toList();
    }
}
