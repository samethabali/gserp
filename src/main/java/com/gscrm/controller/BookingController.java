package com.gscrm.controller;

import com.gscrm.dto.request.AppointmentCreateRequest;
import com.gscrm.dto.response.ApiResponse;
import com.gscrm.dto.response.PublicStaffResponse;
import com.gscrm.dto.response.AppointmentResponse;
import com.gscrm.model.ServiceDefinition;
import com.gscrm.model.enums.StaffRole;
import com.gscrm.repository.ServiceDefinitionRepository;
import com.gscrm.repository.StaffRepository;
import com.gscrm.security.BookingAbuseGuard;
import com.gscrm.service.AppointmentService;
import com.gscrm.service.AvailabilityService;
import com.gscrm.service.ConsentService;
import com.gscrm.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/booking")
@RequiredArgsConstructor
public class BookingController {

    /** Formu bundan hızlı dolduran insan yok. */
    private static final long MIN_FORM_FILL_MILLIS = 3_000L;

    /**
     * IP başına günlük istek tavanı.
     *
     * <p>Salon ayarı değil, uygulama ayarı: bu bir iş kuralı değil kötüye kullanım
     * freni. Salon ayarı yapılsaydı controller her istekte tenant bağlamına bağımlı
     * hâle gelirdi ve limitin kendisi kiracı tarafından gevşetilebilirdi.
     */
    @Value("${app.booking.max-requests-per-ip-per-day:10}")
    private int maxRequestsPerIpPerDay;

    private final ServiceDefinitionRepository serviceRepository;
    private final StaffRepository staffRepository;
    private final AppointmentService appointmentService;
    private final AvailabilityService availabilityService;
    private final ConsentService consentService;
    private final BookingAbuseGuard bookingAbuseGuard;

    @GetMapping("/services")
    public ResponseEntity<ApiResponse<List<ServiceDefinition>>> getServices() {
        Long salonId = TenantContext.requireSalonId();
        return ResponseEntity.ok(ApiResponse.ok(serviceRepository.findBySalonIdAndActiveTrue(salonId)));
    }

    /**
     * Randevu alinabilecek uzmanlar.
     *
     * <p>Uzman filtresi bilerek burada: {@code PublicStaffResponse} personelin
     * rolunu disariya vermiyor, dolayisiyla arayuz bu ayrimi yapamaz. Filtre
     * istemcide dururken bu uc yalnizca ad/renk donmeye gecince sessizce bos
     * liste uretiyordu — resepsiyonist randevu ekraninda cikmasin diye konulan
     * kural, hicbir uzmanin cikmamasina donusmustu.
     */
    @GetMapping("/staff")
    public ResponseEntity<ApiResponse<List<PublicStaffResponse>>> getStaff() {
        Long salonId = TenantContext.requireSalonId();
        List<PublicStaffResponse> staff = staffRepository
                .findBySalonIdAndActiveTrueAndRole(salonId, StaffRole.SPECIALIST).stream()
                .map(PublicStaffResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(staff));
    }

    @GetMapping("/availability")
    public ResponseEntity<ApiResponse<List<AvailabilityService.TimeSlot>>> getAvailability(
            @RequestParam Long staffId,
            @RequestParam Long serviceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        return ResponseEntity.ok(ApiResponse.ok(availabilityService.slotsFor(staffId, serviceId, date)));
    }

    @PostMapping("/request")
    public ResponseEntity<ApiResponse<AppointmentResponse>> book(
            @Valid @RequestBody AppointmentCreateRequest request,
            HttpServletRequest httpRequest) {

        if (isLikelyBot(request)) {
            // Sessiz tuzak: bota reddedildiğini söylemiyoruz, yoksa hangi sinyale
            // takıldığını öğrenip bir sonraki denemede atlar. Hiçbir şey kaydedilmez.
            log.warn("Randevu isteği bot filtresine takıldı (tuzak alan veya anında gönderim)");
            return ResponseEntity.ok(ApiResponse.ok(
                    "Randevu isteğiniz alındı, salon onayı bekleniyor", null));
        }

        if (!bookingAbuseGuard.tryConsume(httpRequest, maxRequestsPerIpPerDay)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiResponse.error(
                    "Bugün için randevu isteği sınırına ulaşıldı. Lütfen salonu telefonla arayın."));
        }

        if (request.getConsentTypes() != null && !request.getConsentTypes().isEmpty()) {
            consentService.findOrCreateCustomerForBooking(
                    request.getCustomerName(), request.getCustomerPhone(), request.getConsentTypes());
        }
        AppointmentResponse response = appointmentService.createRequest(request);
        return ResponseEntity.ok(ApiResponse.ok("Randevu isteğiniz alındı, salon onayı bekleniyor", response));
    }

    /** Tuzak alan dolu ya da form insan hızının çok altında dolduruldu. */
    private boolean isLikelyBot(AppointmentCreateRequest request) {
        if (request.getWebsite() != null && !request.getWebsite().isBlank()) {
            return true;
        }
        Long elapsed = request.getElapsedMs();
        return elapsed != null && elapsed < MIN_FORM_FILL_MILLIS;
    }
}
