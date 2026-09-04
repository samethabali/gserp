package com.gscrm.service;

import com.gscrm.dto.request.InviteCreateRequest;
import com.gscrm.dto.request.TenantProvisionRequest;
import com.gscrm.dto.response.TenantProvisionResponse;
import com.gscrm.model.InviteCode;
import com.gscrm.model.InviteRedemption;
import com.gscrm.model.Organization;
import com.gscrm.model.SubscriptionPlan;
import com.gscrm.model.enums.InviteKind;
import com.gscrm.model.enums.OrganizationType;
import com.gscrm.repository.InviteCodeRepository;
import com.gscrm.repository.InviteRedemptionRepository;
import com.gscrm.repository.OrganizationRepository;
import com.gscrm.repository.SubscriptionPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    @Mock
    private ActivityEventService activityEventService;

    private InviteCodeService inviteCodeService;

    @BeforeEach
    void setUp() {
        inviteCodeService = new InviteCodeService(
                inviteCodeRepository, inviteRedemptionRepository,
                organizationRepository, subscriptionPlanRepository, salonProvisioningService,
                activityEventService);
    }

    @Test
    void registerWithoutCodeDenied() {
        TenantProvisionRequest request = new TenantProvisionRequest();
        assertThatThrownBy(() -> inviteCodeService.registerWithInvite(request, IP))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registerWithUnknownCodeDenied() {
        TenantProvisionRequest request = new TenantProvisionRequest();
        request.setInviteCode("GSCRM-XXXX-YYYY");
        when(inviteCodeRepository.findByCodeForUpdate("GSCRM-XXXX-YYYY")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> inviteCodeService.registerWithInvite(request, IP))
                .isInstanceOf(IllegalArgumentException.class);
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

        // Plan, org tipi ve vitrin bayrağı da koddan gelir: davetli bunları seçemez.
        assertThat(request.getPlanCode()).isEqualTo("SOLO");
        assertThat(request.getOrganizationType()).isEqualTo(OrganizationType.STANDALONE);
        assertThat(request.isShowcase()).isFalse();

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

        verify(activityEventService).recordPlatform("REDEEM", "INVITE_CODE", 1L,
                "Davet kodu kullanıldı: GSCRM-ABCD-EFGH → yeni", null, IP);
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
                .isInstanceOf(IllegalArgumentException.class);
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
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Süre kontrolü sınırda da reddeder: {@code expiresAt == now} artık geçerli değil. */
    @Test
    void expiredCodeDenied() {
        InviteCode invite = InviteCode.builder()
                .code("GSCRM-AB12-CD34")
                .kind(InviteKind.PILOT)
                .maxUses(1)
                .usedCount(0)
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .planCode("SOLO")
                .createdAt(LocalDateTime.now().minusDays(2))
                .build();
        when(inviteCodeRepository.findByCodeForUpdate("GSCRM-AB12-CD34")).thenReturn(Optional.of(invite));
        TenantProvisionRequest request = new TenantProvisionRequest();
        request.setInviteCode("GSCRM-AB12-CD34");
        assertThatThrownBy(() -> inviteCodeService.registerWithInvite(request, IP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("süresi");
    }

    /**
     * SHOWCASE davet, vitrin bayrağını isteğe geçirir; bu bayrak provision içinde
     * örnek personel ve müşteri ekilmesini tetikleyen tek sinyal.
     */
    @Test
    void showcaseInviteDrivesProvisionRequest() {
        InviteCode invite = InviteCode.builder()
                .id(5L)
                .code("GSCRM-SHOW-CASE")
                .kind(InviteKind.SHOWCASE)
                .maxUses(1)
                .usedCount(0)
                .planCode("FRANCHISE_STARTER")
                .organizationType(OrganizationType.FRANCHISE)
                .trialDays(30)
                .createdAt(LocalDateTime.now())
                .build();
        when(inviteCodeRepository.findByCodeForUpdate("GSCRM-SHOW-CASE")).thenReturn(Optional.of(invite));
        when(salonProvisioningService.provision(any())).thenReturn(TenantProvisionResponse.builder()
                .organizationId(11L).salonId(12L).salonSlug("vitrin").adminUserId(13L).build());
        when(organizationRepository.findById(11L)).thenReturn(Optional.of(
                Organization.builder().id(11L).name("Vitrin").build()));

        TenantProvisionRequest request = validRequest("GSCRM-SHOW-CASE", "vitrin");
        inviteCodeService.registerWithInvite(request, IP);

        assertThat(request.isShowcase()).isTrue();
        assertThat(request.getPlanCode()).isEqualTo("FRANCHISE_STARTER");
        assertThat(request.getOrganizationType()).isEqualTo(OrganizationType.FRANCHISE);
        assertThat(request.getTrialDays()).isEqualTo(30);
    }

    /**
     * Çok kullanımlı kod tükenmeden ikinci kez bozdurulabilir. V33'te kullanım
     * geçmişinin ayrı tabloya alınmasının sebebi tam olarak bu senaryoydu.
     */
    @Test
    void partialUseKeepsCodeUsable() {
        InviteCode invite = InviteCode.builder()
                .id(3L)
                .code("GSCRM-MULT-IUSE")
                .kind(InviteKind.PILOT)
                .maxUses(3)
                .usedCount(1)
                .planCode("SOLO")
                .trialDays(90)
                .createdAt(LocalDateTime.now())
                .build();
        when(inviteCodeRepository.findByCodeForUpdate("GSCRM-MULT-IUSE")).thenReturn(Optional.of(invite));
        when(salonProvisioningService.provision(any())).thenReturn(TenantProvisionResponse.builder()
                .organizationId(21L).salonId(22L).salonSlug("ikinci").adminUserId(23L).build());
        when(organizationRepository.findById(21L)).thenReturn(Optional.of(
                Organization.builder().id(21L).name("Ikinci").build()));

        inviteCodeService.registerWithInvite(validRequest("GSCRM-MULT-IUSE", "ikinci"), IP);

        ArgumentCaptor<InviteCode> captor = ArgumentCaptor.forClass(InviteCode.class);
        verify(inviteCodeRepository).save(captor.capture());
        assertThat(captor.getValue().getUsedCount()).isEqualTo(2);
        assertThat(inviteCodeService.resolveStatus(captor.getValue())).isEqualTo("PARTIAL");
    }

    // ───────────────────────── create() ─────────────────────────

    /**
     * Plan kodu daha önce serbest metindi; hatalı kod ancak davetli kayıt olmaya
     * çalışırken patlıyordu. Doğrulama artık kodu üreten kişiye hata veriyor.
     */
    @Test
    void createRejectsUnknownPlanCode() {
        when(subscriptionPlanRepository.findByCodeAndActiveTrue("BOGUS")).thenReturn(Optional.empty());
        InviteCreateRequest request = new InviteCreateRequest();
        request.setPlanCode("bogus");
        assertThatThrownBy(() -> inviteCodeService.create(request, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BOGUS");
        verifyNoInteractions(inviteCodeRepository);
    }

    @Test
    void createAppliesDefaultsWhenFieldsOmitted() {
        InviteCreateRequest request = new InviteCreateRequest();
        request.setKind(null);
        request.setPlanCode(null);
        request.setOrganizationType(null);
        request.setMaxUses(null);
        request.setTrialDays(null);
        stubPlanLookupAndSave("SOLO");

        InviteCode created = inviteCodeService.create(request, 42L);

        assertThat(created.getKind()).isEqualTo(InviteKind.PILOT);
        assertThat(created.getPlanCode()).isEqualTo("SOLO");
        assertThat(created.getOrganizationType()).isEqualTo(OrganizationType.STANDALONE);
        assertThat(created.getMaxUses()).isEqualTo(1);
        assertThat(created.getTrialDays()).isEqualTo(90);
        assertThat(created.getUsedCount()).isZero();
        assertThat(created.getRevokedAt()).isNull();
        assertThat(created.getCreatedBy()).isEqualTo(42L);
        // Alfabede 0/1/I/L/O yok: kod telefonda okunurken karışmasın diye.
        assertThat(created.getCode()).matches("GSCRM-[2-9A-HJKMNP-Z]{4}-[2-9A-HJKMNP-Z]{4}");
    }

    @Test
    void createNormalizesPlanCode() {
        InviteCreateRequest request = new InviteCreateRequest();
        request.setPlanCode("  solo  ");
        stubPlanLookupAndSave("SOLO");

        assertThat(inviteCodeService.create(request, 1L).getPlanCode()).isEqualTo("SOLO");
    }

    @Test
    void createRejectsPastExpiry() {
        when(subscriptionPlanRepository.findByCodeAndActiveTrue("SOLO"))
                .thenReturn(Optional.of(SubscriptionPlan.builder().id(1L).code("SOLO").name("Solo").build()));
        InviteCreateRequest request = new InviteCreateRequest();
        request.setExpiresAt(LocalDateTime.now().minusDays(1));

        assertThatThrownBy(() -> inviteCodeService.create(request, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("gelecekte");
        verifyNoInteractions(inviteCodeRepository);
    }

    @Test
    void createRetriesUntilCodeIsUnique() {
        when(subscriptionPlanRepository.findByCodeAndActiveTrue("SOLO"))
                .thenReturn(Optional.of(SubscriptionPlan.builder().id(1L).code("SOLO").name("Solo").build()));
        when(inviteCodeRepository.existsByCode(anyString())).thenReturn(true, true, false);
        when(inviteCodeRepository.save(any(InviteCode.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(inviteCodeService.create(new InviteCreateRequest(), 1L).getCode()).startsWith("GSCRM-");
        verify(inviteCodeRepository, org.mockito.Mockito.times(3)).existsByCode(anyString());
    }

    // ───────────────────────── revoke / durum / eşleme ─────────────────────────

    @Test
    void revokeStampsRevokedAt() {
        InviteCode invite = InviteCode.builder().id(7L).code("GSCRM-AB12-CD34")
                .kind(InviteKind.PILOT).maxUses(1).usedCount(0).planCode("SOLO")
                .createdAt(LocalDateTime.now()).build();
        when(inviteCodeRepository.findById(7L)).thenReturn(Optional.of(invite));
        when(inviteCodeRepository.save(any(InviteCode.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(inviteCodeService.revoke(7L).getRevokedAt()).isNotNull();
    }

    /** İkinci iptal ilk iptalin zamanını ezmemeli: kod ne zaman kapandı bilgisi korunur. */
    @Test
    void revokeIsIdempotent() {
        LocalDateTime firstRevoke = LocalDateTime.now().minusDays(3);
        InviteCode invite = InviteCode.builder().id(7L).code("GSCRM-AB12-CD34")
                .kind(InviteKind.PILOT).maxUses(1).usedCount(0).planCode("SOLO")
                .revokedAt(firstRevoke).createdAt(LocalDateTime.now().minusDays(5)).build();
        when(inviteCodeRepository.findById(7L)).thenReturn(Optional.of(invite));
        when(inviteCodeRepository.save(any(InviteCode.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(inviteCodeService.revoke(7L).getRevokedAt()).isEqualTo(firstRevoke);
    }

    @Test
    void revokeUnknownIdThrows() {
        when(inviteCodeRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> inviteCodeService.revoke(99L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Panel rozetinin önceliği: iptal &gt; süre doldu &gt; tükendi &gt; kullanımda &gt; yeni. */
    @Test
    void resolveStatusFollowsPriority() {
        assertThat(inviteCodeService.resolveStatus(status(0, 1, null, null))).isEqualTo("ACTIVE");
        assertThat(inviteCodeService.resolveStatus(status(1, 3, null, null))).isEqualTo("PARTIAL");
        assertThat(inviteCodeService.resolveStatus(status(3, 3, null, null))).isEqualTo("USED");

        LocalDateTime past = LocalDateTime.now().minusMinutes(1);
        // Süre dolumu, tükenmenin önüne geçer.
        assertThat(inviteCodeService.resolveStatus(status(3, 3, past, null))).isEqualTo("EXPIRED");
        // İptal her şeyin önüne geçer.
        assertThat(inviteCodeService.resolveStatus(status(3, 3, past, past))).isEqualTo("REVOKED");
    }

    @Test
    void toMapFallsBackToStandaloneWhenOrganizationTypeMissing() {
        InviteCode invite = status(0, 1, null, null);
        invite.setOrganizationType(null);

        Map<String, Object> row = inviteCodeService.toMap(invite);

        assertThat(row).containsEntry("organizationType", "STANDALONE")
                .containsEntry("status", "ACTIVE")
                .containsEntry("kind", "PILOT");
    }

    /** Boş liste kısa devre: {@code WHERE id IN ()} sorgusu hiç kurulmasın. */
    @Test
    void redemptionsForEmptyListSkipsRepository() {
        assertThat(inviteCodeService.redemptionsFor(List.of())).isEmpty();
        verifyNoInteractions(inviteRedemptionRepository);
    }

    // ───────────────────────── yardımcılar ─────────────────────────

    private void stubPlanLookupAndSave(String planCode) {
        when(subscriptionPlanRepository.findByCodeAndActiveTrue(planCode))
                .thenReturn(Optional.of(SubscriptionPlan.builder().id(1L).code(planCode).name(planCode).build()));
        when(inviteCodeRepository.existsByCode(anyString())).thenReturn(false);
        when(inviteCodeRepository.save(any(InviteCode.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static InviteCode status(int usedCount, int maxUses, LocalDateTime expiresAt, LocalDateTime revokedAt) {
        return InviteCode.builder()
                .id(1L)
                .code("GSCRM-AB12-CD34")
                .kind(InviteKind.PILOT)
                .maxUses(maxUses)
                .usedCount(usedCount)
                .expiresAt(expiresAt)
                .revokedAt(revokedAt)
                .planCode("SOLO")
                .trialDays(90)
                .createdAt(LocalDateTime.now().minusDays(1))
                .build();
    }

    private static TenantProvisionRequest validRequest(String inviteCode, String slug) {
        TenantProvisionRequest request = new TenantProvisionRequest();
        request.setInviteCode(inviteCode);
        request.setOrganizationName("Org");
        request.setSalonName("Salon");
        request.setSalonSlug(slug);
        request.setAdminUsername("adminx");
        request.setAdminPassword("password1");
        return request;
    }
}
