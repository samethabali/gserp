package com.gscrm.service;

import com.gscrm.dto.request.InviteCreateRequest;
import com.gscrm.dto.request.TenantProvisionRequest;
import com.gscrm.dto.response.TenantProvisionResponse;
import com.gscrm.model.InviteCode;
import com.gscrm.model.InviteRedemption;
import com.gscrm.model.enums.InviteKind;
import com.gscrm.model.enums.OrganizationType;
import com.gscrm.repository.InviteCodeRepository;
import com.gscrm.repository.InviteRedemptionRepository;
import com.gscrm.repository.OrganizationRepository;
import com.gscrm.repository.SubscriptionPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InviteCodeService {

    /** 0/1/I/L/O dışlandığı için okunurken karışabilecek karakter yok. */
    private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final String PREFIX = "GSCRM";
    private static final int BLOCK = 4;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final InviteCodeRepository inviteCodeRepository;
    private final InviteRedemptionRepository inviteRedemptionRepository;
    private final OrganizationRepository organizationRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final SalonProvisioningService salonProvisioningService;

    @Transactional
    public InviteCode create(InviteCreateRequest request, Long createdBy) {
        String planCode = request.getPlanCode() != null && !request.getPlanCode().isBlank()
                ? request.getPlanCode().trim().toUpperCase(Locale.ROOT)
                : "SOLO";
        // Plan daha önce serbest metindi: hatalı bir plan kodu ancak müşteri kayıt
        // olmaya çalışırken patlıyordu, yani hatayı davet sahibi değil davetli görüyordu.
        if (subscriptionPlanRepository.findByCodeAndActiveTrue(planCode).isEmpty()) {
            throw new IllegalArgumentException("Geçersiz plan kodu: " + planCode);
        }
        if (request.getExpiresAt() != null && !request.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Son kullanma tarihi gelecekte olmalı");
        }

        InviteCode invite = InviteCode.builder()
                .code(generateUniqueCode())
                .kind(request.getKind() != null ? request.getKind() : InviteKind.PILOT)
                .maxUses(request.getMaxUses() != null ? request.getMaxUses() : 1)
                .usedCount(0)
                .expiresAt(request.getExpiresAt())
                .planCode(planCode)
                .organizationType(request.getOrganizationType() != null
                        ? request.getOrganizationType() : OrganizationType.STANDALONE)
                .trialDays(request.getTrialDays() != null ? request.getTrialDays() : 90)
                .note(request.getNote())
                .createdBy(createdBy)
                .createdAt(LocalDateTime.now())
                .build();
        return inviteCodeRepository.save(invite);
    }

    public List<InviteCode> list() {
        return inviteCodeRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public InviteCode revoke(Long id) {
        InviteCode invite = inviteCodeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Davet kodu bulunamadı"));
        if (invite.getRevokedAt() == null) {
            invite.setRevokedAt(LocalDateTime.now());
        }
        return inviteCodeRepository.save(invite);
    }

    /**
     * Davet kodunu bozdurup kiracıyı açar.
     *
     * <p>Kodun satırı pesimistik kilitle okunur; kullanım sayacı, kullanım geçmişi
     * satırı ve organizasyonun ters bağlantısı aynı transaction icinde yazılır, böylece
     * eşzamanlı iki kayıt tek kullanımlık bir kodu iki kez tüketemez.
     */
    @Transactional
    public TenantProvisionResponse registerWithInvite(TenantProvisionRequest request, String ip) {
        String raw = request.getInviteCode();
        if (raw == null || raw.isBlank()) {
            throw new AccessDeniedException("Geçerli bir davet kodu gerekli");
        }
        String code = normalize(raw);
        InviteCode invite = inviteCodeRepository.findByCodeForUpdate(code)
                .orElseThrow(() -> new AccessDeniedException("Davet kodu geçersiz"));
        assertRedeemable(invite);

        request.setPlanCode(invite.getPlanCode());
        request.setOrganizationType(invite.getOrganizationType());
        request.setShowcase(invite.getKind() == InviteKind.SHOWCASE);
        request.setTrialDays(invite.getTrialDays());

        TenantProvisionResponse result = salonProvisioningService.provision(request);

        invite.setUsedCount(invite.getUsedCount() + 1);
        inviteCodeRepository.save(invite);

        inviteRedemptionRepository.save(InviteRedemption.builder()
                .inviteCodeId(invite.getId())
                .organizationId(result.getOrganizationId())
                .salonId(result.getSalonId())
                .salonSlug(result.getSalonSlug())
                .adminUserId(result.getAdminUserId())
                .ip(ip)
                .redeemedAt(LocalDateTime.now())
                .build());

        // Ters arama: "bu işletme hangi kodla geldi?"
        organizationRepository.findById(result.getOrganizationId()).ifPresent(org -> {
            org.setInviteCodeId(invite.getId());
            organizationRepository.save(org);
        });

        return result;
    }

    public List<InviteRedemption> redemptionsFor(List<Long> inviteCodeIds) {
        return inviteCodeIds.isEmpty()
                ? List.of()
                : inviteRedemptionRepository.findByInviteCodeIdInOrderByRedeemedAtDesc(inviteCodeIds);
    }

    public Map<String, Object> toMap(InviteCode invite) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", invite.getId());
        row.put("code", invite.getCode());
        row.put("kind", invite.getKind().name());
        row.put("maxUses", invite.getMaxUses());
        row.put("usedCount", invite.getUsedCount());
        row.put("expiresAt", invite.getExpiresAt());
        row.put("revokedAt", invite.getRevokedAt());
        row.put("planCode", invite.getPlanCode());
        row.put("organizationType", invite.getOrganizationType() != null
                ? invite.getOrganizationType().name() : OrganizationType.STANDALONE.name());
        row.put("trialDays", invite.getTrialDays());
        row.put("note", invite.getNote());
        row.put("createdAt", invite.getCreatedAt());
        row.put("status", resolveStatus(invite));
        return row;
    }

    /** Panelde rozet olarak gösterilen tekil durum; öncelik: iptal &gt; süre doldu &gt; tükendi &gt; kullanımda &gt; yeni. */
    public String resolveStatus(InviteCode invite) {
        if (invite.getRevokedAt() != null) {
            return "REVOKED";
        }
        if (invite.getExpiresAt() != null && !invite.getExpiresAt().isAfter(LocalDateTime.now())) {
            return "EXPIRED";
        }
        if (invite.getUsedCount() >= invite.getMaxUses()) {
            return "USED";
        }
        return invite.getUsedCount() > 0 ? "PARTIAL" : "ACTIVE";
    }

    private void assertRedeemable(InviteCode invite) {
        if (invite.getRevokedAt() != null) {
            throw new AccessDeniedException("Bu davet kodu iptal edilmiş");
        }
        if (invite.getExpiresAt() != null && !invite.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new AccessDeniedException("Davet kodunun süresi dolmuş");
        }
        if (invite.getUsedCount() >= invite.getMaxUses()) {
            throw new AccessDeniedException("Davet kodu kullanım hakkını doldurmuş");
        }
    }

    private String generateUniqueCode() {
        for (int i = 0; i < 20; i++) {
            String code = PREFIX + "-" + randomBlock() + "-" + randomBlock();
            if (!inviteCodeRepository.existsByCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Davet kodu üretilemedi");
    }

    private String randomBlock() {
        StringBuilder sb = new StringBuilder(BLOCK);
        for (int i = 0; i < BLOCK; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /**
     * Elle yazılmış kodu kanonik biçime getirir.
     *
     * <p>Kod e-posta veya mesajla paylaşılıyor; kullanıcı onu küçük harfle, tiresiz
     * ya da boşluklu yazıyor. Katı eşleşme, geçerli bir kodu "geçersiz" gösterip
     * kaydı daha ilk adımda durduruyordu. {@code gscrm a7k2m4xq}, {@code A7K2-M4XQ}
     * ve {@code gscrm-a7k2-m4xq} artık aynı koda çözülür.
     */
    String normalize(String raw) {
        String cleaned = raw.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (cleaned.startsWith(PREFIX)) {
            cleaned = cleaned.substring(PREFIX.length());
        }
        if (cleaned.length() != BLOCK * 2) {
            // Beklenen uzunlukta değil: olduğu gibi bırak, arama sonuçsuz kalsın.
            return PREFIX + "-" + cleaned;
        }
        return PREFIX + "-" + cleaned.substring(0, BLOCK) + "-" + cleaned.substring(BLOCK);
    }
}
