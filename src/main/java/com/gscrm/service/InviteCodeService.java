package com.gscrm.service;

import com.gscrm.dto.request.TenantProvisionRequest;
import com.gscrm.dto.response.TenantProvisionResponse;
import com.gscrm.model.InviteCode;
import com.gscrm.model.enums.InviteKind;
import com.gscrm.model.enums.OrganizationType;
import com.gscrm.repository.InviteCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InviteCodeService {

    private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final InviteCodeRepository inviteCodeRepository;
    private final SalonProvisioningService salonProvisioningService;

    @Transactional
    public InviteCode create(InviteKind kind, Integer maxUses, LocalDateTime expiresAt,
                             String note, String planCode, OrganizationType organizationType, Long createdBy) {
        InviteKind resolvedKind = kind != null ? kind : InviteKind.PILOT;
        InviteCode invite = InviteCode.builder()
                .code(generateUniqueCode())
                .kind(resolvedKind)
                .maxUses(maxUses != null && maxUses > 0 ? maxUses : 1)
                .usedCount(0)
                .expiresAt(expiresAt)
                .planCode(planCode != null && !planCode.isBlank() ? planCode : "SOLO")
                .organizationType(organizationType != null ? organizationType : OrganizationType.STANDALONE)
                .note(note)
                .createdBy(createdBy)
                .createdAt(LocalDateTime.now())
                .build();
        return inviteCodeRepository.save(invite);
    }

    @Transactional(readOnly = true)
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

    @Transactional
    public TenantProvisionResponse registerWithInvite(TenantProvisionRequest request) {
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

        TenantProvisionResponse result = salonProvisioningService.provision(request);
        invite.setUsedCount(invite.getUsedCount() + 1);
        invite.setRedeemedOrganizationId(result.getOrganizationId());
        inviteCodeRepository.save(invite);
        return result;
    }

    public Map<String, Object> toMap(InviteCode invite) {
        return Map.ofEntries(
                Map.entry("id", invite.getId()),
                Map.entry("code", invite.getCode()),
                Map.entry("kind", invite.getKind().name()),
                Map.entry("maxUses", invite.getMaxUses()),
                Map.entry("usedCount", invite.getUsedCount()),
                Map.entry("expiresAt", invite.getExpiresAt() != null ? invite.getExpiresAt() : ""),
                Map.entry("revokedAt", invite.getRevokedAt() != null ? invite.getRevokedAt() : ""),
                Map.entry("planCode", invite.getPlanCode()),
                Map.entry("note", invite.getNote() != null ? invite.getNote() : ""),
                Map.entry("createdAt", invite.getCreatedAt()),
                Map.entry("redeemedOrganizationId", invite.getRedeemedOrganizationId() != null ? invite.getRedeemedOrganizationId() : "")
        );
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
            String code = "GSCRM-" + randomBlock(4) + "-" + randomBlock(4);
            if (!inviteCodeRepository.existsByCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Davet kodu üretilemedi");
    }

    private String randomBlock(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    private String normalize(String code) {
        return code.trim().toUpperCase(Locale.ROOT).replace(' ', '-');
    }
}
