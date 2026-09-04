package com.gscrm.controller;

import com.gscrm.dto.response.ApiResponse;
import com.gscrm.dto.response.AppointmentResponse;
import com.gscrm.model.Appointment;
import com.gscrm.model.Customer;
import com.gscrm.model.enums.AppointmentStatus;
import com.gscrm.repository.AppointmentRepository;
import com.gscrm.security.ClientIpResolver;
import com.gscrm.service.AppointmentService;
import com.gscrm.service.CampaignService;
import com.gscrm.service.CustomerMatchingService;
import com.gscrm.service.VerificationCodeService;
import com.gscrm.tenant.TenantContext;
import com.gscrm.util.PhoneNormalizer;
import com.gscrm.validation.PhoneNumber;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Online randevu için telefon doğrulama uçları.
 *
 * <p>Yol bilerek {@code /api/booking/**} altında: o namespace SecurityConfig'te
 * zaten {@code permitAll} ve CSRF muafı, {@code TenantFilter} de public isteklerde
 * çalıştığı için salon bağlamı çözülüyor. Yani güvenlik yapılandırmasına <b>hiçbir
 * ekleme gerekmiyor</b>. Hız sınırları {@code RateLimitFilter} içinde bu iki uca
 * özel olarak tanımlı (3/dk ve 6/dk).
 *
 * <p>Geçmiş ve sadakat bilgisi ayrı bir uç yerine {@code confirm} yanıtının içinde
 * dönüyor: ayrı uç kendi yetkilendirme hikâyesini gerektirirdi ve mevcut
 * {@code /api/campaigns/loyalty-info} anonim çağrılamıyor.
 */
@RestController
@RequestMapping("/api/booking/verify")
@RequiredArgsConstructor
public class BookingVerificationController {

    /** Tanınan müşteriye gösterilecek geçmiş randevu sayısı. */
    private static final int RECENT_HISTORY_LIMIT = 5;

    private final VerificationCodeService verificationCodeService;
    private final CustomerMatchingService customerMatchingService;
    private final CampaignService campaignService;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentService appointmentService;
    private final ClientIpResolver clientIpResolver;

    public record StartRequest(
            @NotBlank(message = "Telefon numarası girilmelidir")
            @PhoneNumber String phone) {
    }

    public record ConfirmRequest(
            @NotBlank(message = "Telefon numarası girilmelidir")
            @PhoneNumber String phone,
            @NotBlank(message = "Kod girilmelidir")
            @Size(min = 4, max = 8, message = "Kod geçersiz") String code) {
    }

    /**
     * Doğrulama kodu ister.
     *
     * <p>Yanıt, numara sistemde kayıtlı olsun olmasın birebir aynıdır — aksi hâlde
     * bu uç "bu numara müşteriniz mi?" sorusuna cevap veren bir sorgulama aracına
     * dönüşürdü.
     */
    @PostMapping("/start")
    public ResponseEntity<ApiResponse<Map<String, Object>>> start(
            @Valid @RequestBody StartRequest request, HttpServletRequest httpRequest) {

        VerificationCodeService.StartResult result =
                verificationCodeService.start(request.phone(), clientIpResolver.resolve(httpRequest));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", result.enabled());
        body.put("sent", result.sent());
        body.put("resendAfterSeconds", result.resendAfterSeconds());
        body.put("expiresInSeconds", result.expiresInSeconds());

        return ResponseEntity.ok(result.message() != null
                ? ApiResponse.ok(result.message(), body)
                : ApiResponse.ok(body));
    }

    /**
     * Kodu doğrular; başarılıysa tek kullanımlık token ve — varsa — müşterinin
     * tanınma bilgisini döner.
     *
     * <p>İsim, geçmiş ve sadakat yalnızca burada, yani kod doğrulandıktan sonra
     * açığa çıkar. Numarayı bilmek yetmez, numaraya sahip olmak gerekir.
     */
    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<Map<String, Object>>> confirm(
            @Valid @RequestBody ConfirmRequest request, HttpServletRequest httpRequest) {

        VerificationCodeService.ConfirmResult result = verificationCodeService.confirm(
                request.phone(), request.code(), clientIpResolver.resolve(httpRequest));

        if (!result.verified()) {
            return ResponseEntity.status(400).body(ApiResponse.error(result.message()));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("verificationToken", result.verificationToken());

        Customer customer = customerMatchingService.findByPhone(request.phone()).orElse(null);
        body.put("recognized", customer != null);

        if (customer != null) {
            body.put("firstName", customer.getFirstName());
            body.put("lastName", customer.getLastName());
            body.put("pastAppointments", recentHistory(customer));

            CampaignService.LoyaltyInfo loyalty = campaignService.getLoyaltyInfo(customer.getPhone());
            body.put("loyalty", loyalty);
        }

        return ResponseEntity.ok(ApiResponse.ok(result.message(), body));
    }

    private List<AppointmentResponse> recentHistory(Customer customer) {
        String normalized = PhoneNormalizer.normalizeOrNull(customer.getPhone());
        if (normalized == null) return List.of();

        List<Appointment> past = appointmentRepository
                .findBySalonIdAndCustomerPhoneNormalizedAndStartTimeBeforeOrderByStartTimeDesc(
                        TenantContext.requireSalonId(), normalized, LocalDateTime.now());

        return past.stream()
                .filter(a -> a.getStatus() != AppointmentStatus.CANCELLED)
                .limit(RECENT_HISTORY_LIMIT)
                .map(appointmentService::toResponse)
                .toList();
    }
}
