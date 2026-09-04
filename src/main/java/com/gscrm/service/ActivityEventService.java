package com.gscrm.service;

import com.gscrm.model.ActivityEvent;
import com.gscrm.repository.ActivityEventRepository;
import com.gscrm.repository.CustomerRepository;
import com.gscrm.security.AuthenticatedUser;
import com.gscrm.tenant.TenantContext;
import com.gscrm.util.PhoneNormalizer;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivityEventService {

    public static final String SCOPE_TENANT = "TENANT";
    public static final String SCOPE_PLATFORM = "PLATFORM";

    public static final String OUTCOME_SUCCESS = "SUCCESS";
    public static final String OUTCOME_DENIED = "DENIED";
    public static final String OUTCOME_ERROR = "ERROR";

    /**
     * İstek başına işaret: bu istek için ayrıntılı bir kayıt yazıldı.
     *
     * <p>{@code ActivityAuditFilter} her yazma isteği için jenerik bir satır üretiyor;
     * servis katmanı da aynı işlem için anlamlı bir satır yazıyordu. Sonuç: randevu,
     * müşteri ve ödeme işlemleri kütükte iki kez görünüyordu.
     */
    public static final String REQUEST_ATTR_RECORDED = "gscrm.activity.recorded";

    private final ActivityEventRepository activityEventRepository;
    private final CustomerRepository customerRepository;

    @Transactional
    public void record(String action, String entityType, Long entityId, Long customerId,
                       String summary, String detail, String ip) {
        record(ActivityEvent.builder()
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .customerId(customerId)
                .summary(summary)
                .detail(detail)
                .ip(ip));
    }

    @Transactional
    public void record(String action, String entityType, Long entityId, Long customerId, String summary) {
        record(action, entityType, entityId, customerId, summary, null, null);
    }

    /** Eski/yeni değer farkını {@code detail} alanına yazan kısayol. */
    @Transactional
    public void recordChange(String action, String entityType, Long entityId, Long customerId,
                             String summary, String diffJson) {
        record(action, entityType, entityId, customerId, summary, diffJson, null);
    }

    /**
     * Kiracı bağlamı olmayan platform işlemleri (davet kodu, kiracı açma, askıya alma).
     *
     * <p>Bunlar daha önce hiç loglanmıyordu: {@code salon_id} zorunlu olduğu ve
     * platform uçları kiracı çözümlemesini atladığı için kayıt sessizce düşüyordu.
     */
    @Transactional
    public void recordPlatform(String action, String entityType, Long entityId,
                               String summary, String detail, String ip) {
        record(ActivityEvent.builder()
                .scope(SCOPE_PLATFORM)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .summary(summary)
                .detail(detail)
                .ip(ip));
    }

    /**
     * Servis katmanının ayrıca kaydetmediği HTTP yazma işlemleri.
     *
     * <p>Yanıt kodu da saklanır: reddedilen (4xx) ve hata veren (5xx) istekler artık
     * kütükte görünür, önceden tamamen atlanıyorlardı.
     */
    @Transactional
    public void recordHttp(String method, Long customerId, String summary, int status, String ip) {
        String outcome = status >= 500 ? OUTCOME_ERROR : status >= 400 ? OUTCOME_DENIED : OUTCOME_SUCCESS;
        activityEventRepository.save(ActivityEvent.builder()
                .salonId(TenantContext.getSalonId())
                .scope(SCOPE_TENANT)
                .outcome(outcome)
                .httpStatus(status)
                .customerId(customerId)
                .actorUserId(currentUserId())
                .actorUsername(currentUsername())
                .action(method)
                .entityType("HTTP")
                .summary(truncate(summary, 512))
                .ip(ip)
                .createdAt(LocalDateTime.now())
                .build());
    }

    /** Kimlik doğrulama olayları — giriş, çıkış, başarısız deneme, parola değişimi. */
    @Transactional
    public void recordAuth(String action, String username, Long userId, Long salonId,
                           String outcome, String summary, String ip) {
        activityEventRepository.save(ActivityEvent.builder()
                .salonId(salonId)
                .scope(salonId != null ? SCOPE_TENANT : SCOPE_PLATFORM)
                .outcome(outcome)
                .actorUserId(userId)
                .actorUsername(username != null ? truncate(username, 64) : "anonim")
                .action(action)
                .entityType("AUTH")
                .summary(truncate(summary, 512))
                .ip(ip)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private void record(ActivityEvent.ActivityEventBuilder builder) {
        AuthenticatedUser actor = currentUser();
        ActivityEvent event = builder
                .salonId(TenantContext.getSalonId())
                .actorUserId(actor != null ? actor.getId() : null)
                .actorUsername(actor != null ? actor.getUsername() : "anonim")
                .createdAt(LocalDateTime.now())
                .build();
        if (event.getScope() == null) {
            event.setScope(TenantContext.getSalonId() != null ? SCOPE_TENANT : SCOPE_PLATFORM);
        }
        if (event.getSummary() == null) {
            event.setSummary(event.getAction());
        } else {
            event.setSummary(truncate(event.getSummary(), 512));
        }
        activityEventRepository.save(event);
        markRecorded();
    }

    @Transactional
    public void recordForCustomerPhone(String action, String entityType, Long entityId,
                                       String customerPhone, String summary) {
        recordForCustomerPhone(action, entityType, entityId, customerPhone, summary, null);
    }

    @Transactional
    public void recordForCustomerPhone(String action, String entityType, Long entityId,
                                       String customerPhone, String summary, String detail) {
        Long customerId = null;
        Long salonId = TenantContext.getSalonId();
        if (salonId != null && customerPhone != null && !customerPhone.isBlank()) {
            String normalized = PhoneNormalizer.normalizeOrNull(customerPhone);
            if (normalized != null) {
                customerId = customerRepository.findBySalonIdAndPhoneNormalized(salonId, normalized).stream()
                        .findFirst()
                        .map(c -> c.getId())
                        .orElse(null);
            }
        }
        record(action, entityType, entityId, customerId, summary, detail, null);
    }

    @Transactional(readOnly = true)
    public List<ActivityEvent> listRecent(int limit) {
        Long salonId = TenantContext.requireSalonId();
        int capped = Math.min(Math.max(limit, 1), 200);
        return activityEventRepository.findBySalonIdOrderByCreatedAtDesc(salonId, PageRequest.of(0, capped));
    }

    /** Kütük sayfası için filtrelenebilir, sayfalanmış görünüm. */
    @Transactional(readOnly = true)
    public Page<ActivityEvent> search(LocalDateTime from, LocalDateTime to, String action,
                                      String username, String query, Pageable pageable) {
        Long salonId = TenantContext.requireSalonId();
        return activityEventRepository.search(salonId, from, to,
                blankToNull(action), blankToNull(username), blankToNull(query), pageable);
    }

    /** Kütük sayfasının işlem türü filtresi; listeyi verinin kendisi belirler. */
    @Transactional(readOnly = true)
    public List<String> distinctActions() {
        return activityEventRepository.findDistinctActions(TenantContext.requireSalonId());
    }

    @Transactional(readOnly = true)
    public List<ActivityEvent> listForCustomer(Long customerId, int limit) {
        Long salonId = TenantContext.requireSalonId();
        int capped = Math.min(Math.max(limit, 1), 200);
        return activityEventRepository.findBySalonIdAndCustomerIdOrderByCreatedAtDesc(
                salonId, customerId, PageRequest.of(0, capped));
    }

    /** Platform paneli için kiracı sınırı olmayan akış. */
    @Transactional(readOnly = true)
    public List<ActivityEvent> listPlatformFeed(Long salonId, int limit) {
        int capped = Math.min(Math.max(limit, 1), 200);
        PageRequest page = PageRequest.of(0, capped);
        return salonId != null
                ? activityEventRepository.findBySalonIdOrderByCreatedAtDesc(salonId, page)
                : activityEventRepository.findAllByOrderByCreatedAtDesc(page);
    }

    private Long currentUserId() {
        AuthenticatedUser user = currentUser();
        return user != null ? user.getId() : null;
    }

    private String currentUsername() {
        AuthenticatedUser user = currentUser();
        return user != null ? truncate(user.getUsername(), 64) : "anonim";
    }

    private AuthenticatedUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        return null;
    }

    private void markRecorded() {
        HttpServletRequest request = currentRequest();
        if (request != null) {
            request.setAttribute(REQUEST_ATTR_RECORDED, Boolean.TRUE);
        }
    }

    private HttpServletRequest currentRequest() {
        var attrs = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attrs instanceof org.springframework.web.context.request.ServletRequestAttributes sra) {
            return sra.getRequest();
        }
        return null;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
