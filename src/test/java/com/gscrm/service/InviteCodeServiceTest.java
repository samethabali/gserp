package com.gscrm.service;

import com.gscrm.dto.request.TenantProvisionRequest;
import com.gscrm.dto.response.TenantProvisionResponse;
import com.gscrm.model.InviteCode;
import com.gscrm.model.InviteRedemption;
import com.gscrm.model.Organization;
import com.gscrm.model.enums.InviteKind;
import com.gscrm.repository.InviteCodeRepository;
import com.gscrm.repository.InviteRedemptionRepository;
import com.gscrm.repository.OrganizationRepository;
import com.gscrm.repository.SubscriptionPlanRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InviteCodeServiceTest {

    private static final String IP = "203.0.113.9";

    @Mock
    private InviteCodeRepository inviteCodeRepository;
    @Mock
    private InviteRedemptionRepository inviteRedemptionRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private SubscriptionPlanRepository subscriptionPlanRepository;
    @Mock
    private SalonProvisioningService salonProvisioningService;

    @InjectMocks
    private InviteCodeService inviteCodeService;

    @Test
    void registerWithoutCodeDenied() {
        TenantProvisionRequest request = new TenantProvisionRequest();
        assertThatThrownBy(() -> inviteCodeService.registerWithInvite(request, IP))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void registerWithUnknownCodeDenied() {
        TenantProvisionRequest request = new TenantProvisionRequest();
        request.setInviteCode("GSCRM-XXXX-YYYY");
        when(inviteCodeRepository.findByCodeForUpdate("GSCRM-XXXX-YYYY")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> inviteCodeService.registerWithInvite(request, IP))
                .isInstanceOf(AccessDeniedException.class);
    }

    /**
     * Kod e-posta/mesajla paylaşılıyor ve elle yazılıyor. Katı eşleşme, geçerli bir
     * kodu ilk adımda "geçersiz" gösteriyordu; bu senaryolar kaydın kendisidir.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "GSCRM-ABCD-EFGH",
            "gscrm-abcd-efgh",
            "gscrm abcd efgh",
            "GSCRMABCDEFGH",
            "ABCD-EFGH",
            "abcdefgh",
            "  gscrm-AbCd-eFgH  "
    })
    void normalizesLooselyTypedCodes(String typed) {
        assertThat(inviteCodeService.normalize(typed)).isEqualTo("GSCRM-ABCD-EFGH");
    }

    @Test
    void registerConsumesValidCodeAndRecordsRedemption() {
        InviteCode invite = InviteCode.builder()
                .id(1L)
                .code("GSCRM-ABCD-EFGH")
                .kind(InviteKind.PILOT)
                .maxUses(1)
                .usedCount(0)
                .planCode("SOLO")
                .trialDays(90)
                .createdAt(LocalDateTime.now())
                .build();
        when(inviteCodeRepository.findByCodeForUpdate("GSCRM-ABCD-EFGH")).thenReturn(Optional.of(invite));
        when(salonProvisioningService.provision(any())).thenReturn(TenantProvisionResponse.builder()
                .organizationId(9L)
                .salonId(3L)
                .salonSlug("yeni")
                .adminUserId(7L)
                .build());
        when(organizationRepository.findById(9L)).thenReturn(Optional.of(
                Organization.builder().id(9L).name("Org").build()));

        TenantProvisionRequest request = new TenantProvisionRequest();
        request.setInviteCode("gscrm-abcd-efgh");
        request.setOrganizationName("Org");
        request.setSalonName("Salon");
        request.setSalonSlug("yeni");
        request.setAdminUsername("adminx");
        request.setAdminPassword("password1");

        TenantProvisionResponse result = inviteCodeService.registerWithInvite(request, IP);
        assertThat(result.getOrganizationId()).isEqualTo(9L);

        // Deneme süresi koddan gelir; servis içindeki 14 gün sabiti kaldırıldı.
        assertThat(request.getTrialDays()).isEqualTo(90);

        ArgumentCaptor<InviteCode> captor = ArgumentCaptor.forClass(InviteCode.class);
        verify(inviteCodeRepository).save(captor.capture());
        assertThat(captor.getValue().getUsedCount()).isEqualTo(1);

        // Kullanım artık tek kolonun üzerine yazılmıyor, ayrı satır olarak tutuluyor.
        ArgumentCaptor<InviteRedemption> redemption = ArgumentCaptor.forClass(InviteRedemption.class);
        verify(inviteRedemptionRepository).save(redemption.capture());
        assertThat(redemption.getValue().getInviteCodeId()).isEqualTo(1L);
        assertThat(redemption.getValue().getOrganizationId()).isEqualTo(9L);
        assertThat(redemption.getValue().getSalonId()).isEqualTo(3L);
        assertThat(redemption.getValue().getSalonSlug()).isEqualTo("yeni");
        assertThat(redemption.getValue().getAdminUserId()).isEqualTo(7L);
        assertThat(redemption.getValue().getIp()).isEqualTo(IP);

        // Ters arama: "bu işletme hangi kodla geldi?"
        ArgumentCaptor<Organization> org = ArgumentCaptor.forClass(Organization.class);
        verify(organizationRepository).save(org.capture());
        assertThat(org.getValue().getInviteCodeId()).isEqualTo(1L);
    }

    @Test
    void revokedCodeDenied() {
        InviteCode invite = InviteCode.builder()
                .code("GSCRM-AB12-CD34")
                .kind(InviteKind.PILOT)
                .maxUses(1)
                .usedCount(0)
                .revokedAt(LocalDateTime.now())
                .planCode("SOLO")
                .createdAt(LocalDateTime.now())
                .build();
        when(inviteCodeRepository.findByCodeForUpdate("GSCRM-AB12-CD34")).thenReturn(Optional.of(invite));
        TenantProvisionRequest request = new TenantProvisionRequest();
        request.setInviteCode("GSCRM-AB12-CD34");
        assertThatThrownBy(() -> inviteCodeService.registerWithInvite(request, IP))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void exhaustedCodeDenied() {
        InviteCode invite = InviteCode.builder()
                .code("GSCRM-AB12-CD34")
                .kind(InviteKind.PILOT)
                .maxUses(1)
                .usedCount(1)
                .planCode("SOLO")
                .createdAt(LocalDateTime.now())
                .build();
        when(inviteCodeRepository.findByCodeForUpdate("GSCRM-AB12-CD34")).thenReturn(Optional.of(invite));
        TenantProvisionRequest request = new TenantProvisionRequest();
        request.setInviteCode("GSCRM-AB12-CD34");
        assertThatThrownBy(() -> inviteCodeService.registerWithInvite(request, IP))
                .isInstanceOf(AccessDeniedException.class);
    }
}
