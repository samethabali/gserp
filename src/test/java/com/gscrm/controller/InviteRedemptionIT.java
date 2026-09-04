package com.gscrm.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gscrm.model.InviteCode;
import com.gscrm.model.InviteRedemption;
import com.gscrm.model.Organization;
import com.gscrm.model.OrganizationSubscription;
import com.gscrm.model.Salon;
import com.gscrm.model.User;
import com.gscrm.model.enums.InviteKind;
import com.gscrm.model.enums.OrganizationType;
import com.gscrm.model.enums.UserRole;
import com.gscrm.repository.InviteCodeRepository;
import com.gscrm.repository.InviteRedemptionRepository;
import com.gscrm.repository.OnboardingStateRepository;
import com.gscrm.repository.OrganizationRepository;
import com.gscrm.repository.OrganizationSubscriptionRepository;
import com.gscrm.repository.SalonRepository;
import com.gscrm.repository.UserRepository;
import com.gscrm.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Davet kodunun uçtan uca bozdurulması.
 *
 * <p>{@link InviteRegisterIT} yalnızca iki reddetme senaryosunu kapsıyordu: kodsuz
 * ve geçersiz kodlu kayıt. Kodun gerçekten çalıştığı yol — kod üret, bozdur, kiracı
 * açılsın — hiç test edilmiyordu; oysa pilot müşterinin izlediği tek yol bu. Burada
 * platform panelinden üretilen gerçek bir kodla kayıt olunuyor ve provisioning'in
 * kiracıya bıraktığı her satır tek tek doğrulanıyor.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Davet kodu bozdurma")
class InviteRedemptionIT {

    private static final String IP_HEADER_VALUE = "203.0.113.7";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private InviteCodeRepository inviteCodeRepository;
    @Autowired private InviteRedemptionRepository inviteRedemptionRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private SalonRepository salonRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private OnboardingStateRepository onboardingStateRepository;
    @Autowired private OrganizationSubscriptionRepository subscriptionRepository;

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);

    @Autowired private com.gscrm.security.RateLimitFilter rateLimitFilter;

    private Long platformAdminId;

    /**
     * Kayıt ucunun dakikalık sınırı (5/dk) tüm testlerde aynı IP kovasını
     * paylaşıyor; sıfırlanmazsa üçüncü testten sonra 429 başlar. Aynı temizlik
     * {@code UiSecurityBehaviourIT}'de de yapılıyor, komşu testler bozulmasın diye.
     */
    @BeforeEach
    void resetRateLimits() {
        rateLimitFilter.reset();
    }

    @org.junit.jupiter.api.AfterEach
    void clearRateLimits() {
        rateLimitFilter.reset();
    }

    /**
     * {@code invite_code.created_by} kolonu {@code users(id)}'e bağlı, bu yüzden
     * davet kodunu üreten yöneticinin gerçekten var olması gerekiyor. Platform
     * yöneticisi de bir salona bağlı ({@code users.salon_id} NOT NULL), o yüzden
     * testin kendi kiracısı burada açılıyor.
     */
    @BeforeEach
    void seedPlatformAdmin() {
        LocalDateTime now = LocalDateTime.now();
        Organization org = organizationRepository.save(Organization.builder()
                .name("Davet Platform Org").type(OrganizationType.STANDALONE)
                .active(true).loyaltyPolicy("SALON").createdAt(now).build());
        Salon salon = salonRepository.save(Salon.builder()
                .organizationId(org.getId()).slug("platform-" + suffix).name("Platform Salon")
                .timezone("Europe/Istanbul").active(true).createdAt(now).build());
        platformAdminId = userRepository.save(User.builder()
                .salonId(salon.getId()).organizationId(org.getId())
                .username("platform-admin-" + suffix)
                .passwordHash("$2a$10$abcdefghijklmnopqrstuv")
                .role(UserRole.PLATFORM_ADMIN).enabled(true)
                .mustChangePassword(false).createdAt(now).build()).getId();
    }

    @Test
    @DisplayName("kod bozdurulunca kiracının tüm satırları oluşur")
    void redeemingCodeProvisionsCompleteTenant() throws Exception {
        JsonNode invite = createInvite("{\"planCode\":\"SOLO\",\"trialDays\":90,\"maxUses\":1}");
        String code = invite.path("code").asText();
        long inviteId = invite.path("id").asLong();

        String slug = "davet-" + suffix;
        JsonNode result = register(code, slug, "admin" + suffix);

        long organizationId = result.path("organizationId").asLong();
        long salonId = result.path("salonId").asLong();
        long adminUserId = result.path("adminUserId").asLong();

        Organization org = organizationRepository.findById(organizationId).orElseThrow();
        assertThat(org.getType()).isEqualTo(OrganizationType.STANDALONE);
        assertThat(org.isActive()).isTrue();
        // Ters arama: "bu işletme hangi kodla geldi?"
        assertThat(org.getInviteCodeId()).isEqualTo(inviteId);

        Salon salon = salonRepository.findById(salonId).orElseThrow();
        assertThat(salon.getSlug()).isEqualTo(slug);
        assertThat(salon.isShowcase()).isFalse();
        assertThat(salon.isActive()).isTrue();

        User admin = userRepository.findById(adminUserId).orElseThrow();
        assertThat(admin.getRole()).isEqualTo(UserRole.BRANCH_MANAGER);
        assertThat(admin.isMustChangePassword()).isTrue();
        assertThat(admin.getPasswordHash()).isNotEqualTo("password123");

        assertThat(onboardingStateRepository.findBySalonId(salonId).orElseThrow().getCurrentStep())
                .isEqualTo("SALON_INFO");

        // Deneme süresi koddan gelir; provisioning içindeki sabit süre kaldırılmıştı.
        OrganizationSubscription subscription = subscriptionRepository.findByOrganizationId(organizationId)
                .orElseThrow();
        assertThat(subscription.getStatus()).isEqualTo("TRIAL");
        assertThat(subscription.getTrialEnd())
                .isAfter(LocalDateTime.now().plusDays(89))
                .isBefore(LocalDateTime.now().plusDays(91));

        // Kullanım artık tek kolonun üzerine yazılmıyor, ayrı satır olarak tutuluyor.
        InviteRedemption redemption = inviteRedemptionRepository.findByOrganizationId(organizationId)
                .orElseThrow();
        assertThat(redemption.getInviteCodeId()).isEqualTo(inviteId);
        assertThat(redemption.getSalonId()).isEqualTo(salonId);
        assertThat(redemption.getSalonSlug()).isEqualTo(slug);
        assertThat(redemption.getAdminUserId()).isEqualTo(adminUserId);
        assertThat(redemption.getIp()).isEqualTo(IP_HEADER_VALUE);

        assertThat(inviteCodeRepository.findById(inviteId).orElseThrow().getUsedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("tek kullanımlık kod ikinci kez bozdurulamaz")
    void singleUseCodeCannotBeRedeemedTwice() throws Exception {
        String code = createInvite("{\"planCode\":\"SOLO\",\"maxUses\":1}").path("code").asText();

        register(code, "ilk-" + suffix, "ilk" + suffix);

        // Reddedilen kayıt işlemi geri sarıldığı için bu satırdan sonra DB'ye
        // dokunulmuyor: hata, paylaşılan test transaction'ını rollback-only yapar.
        mockMvc.perform(registerRequest(code, "ikinci-" + suffix, "ikinci" + suffix))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("çok kullanımlı kod tükenene kadar çalışır")
    void multiUseCodeWorksUntilExhausted() throws Exception {
        JsonNode invite = createInvite("{\"planCode\":\"SOLO\",\"maxUses\":2}");
        String code = invite.path("code").asText();
        long inviteId = invite.path("id").asLong();

        register(code, "coklu-a-" + suffix, "coklua" + suffix);
        register(code, "coklu-b-" + suffix, "coklub" + suffix);

        assertThat(inviteCodeRepository.findById(inviteId).orElseThrow().getUsedCount()).isEqualTo(2);

        mockMvc.perform(registerRequest(code, "coklu-c-" + suffix, "cokluc" + suffix))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("iptal edilen kod bozdurulamaz")
    void revokedCodeCannotBeRedeemed() throws Exception {
        JsonNode invite = createInvite("{\"planCode\":\"SOLO\",\"maxUses\":1}");
        String code = invite.path("code").asText();

        mockMvc.perform(post("/api/platform/invites/" + invite.path("id").asLong() + "/revoke")
                        .with(authentication(platformAdmin())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(registerRequest(code, "iptal-" + suffix, "iptal" + suffix))
                .andExpect(status().isBadRequest());
    }

    /**
     * Geçmiş tarihli kod uç üzerinden üretilemiyor ({@code @Future} doğrulaması),
     * bu yüzden satır doğrudan yazılıyor — süresi dolmuş kodun reddi
     * {@code assertRedeemable}'ın uçtan uca test edilmeyen tek dalıydı.
     */
    @Test
    @DisplayName("süresi dolmuş kod bozdurulamaz")
    void expiredCodeCannotBeRedeemed() throws Exception {
        InviteCode expired = inviteCodeRepository.save(InviteCode.builder()
                .code("GSCRM-EXPR-" + suffix.substring(0, 4).toUpperCase())
                .kind(InviteKind.PILOT)
                .maxUses(1)
                .usedCount(0)
                .expiresAt(LocalDateTime.now().minusDays(1))
                .planCode("SOLO")
                .organizationType(OrganizationType.STANDALONE)
                .trialDays(90)
                .createdAt(LocalDateTime.now().minusDays(10))
                .build());

        mockMvc.perform(registerRequest(expired.getCode(), "suresi-" + suffix, "suresi" + suffix))
                .andExpect(status().isBadRequest());
    }

    /**
     * Kod e-posta veya mesajla paylaşılıp elle yazılıyor. Normalize mantığı birim
     * testte var; burada uçtan uca çalıştığı doğrulanıyor.
     */
    @Test
    @DisplayName("kod küçük harfle ve tiresiz yazılsa da kabul edilir")
    void looselyTypedCodeIsAccepted() throws Exception {
        String code = createInvite("{\"planCode\":\"SOLO\",\"maxUses\":1}").path("code").asText();
        String typed = code.toLowerCase().replace("-", " ");

        register(typed, "gevsek-" + suffix, "gevsek" + suffix);

        assertThat(salonRepository.existsBySlug("gevsek-" + suffix)).isTrue();
    }

    /**
     * Yeni kiracı hizmet menüsüyle geliyor ama personelsiz: {@code /api/booking/staff}
     * yalnızca aktif SPECIALIST döndürdüğü için randevu sayfası kayıt anında boş.
     * Bu test mevcut davranışı sabitliyor; ziyaretçiye gösterilen mesaj buna dayanıyor.
     */
    @Test
    @DisplayName("yeni kiracıda hizmet var, personel yok")
    void newTenantHasSeededServicesButNoStaff() throws Exception {
        String code = createInvite("{\"planCode\":\"SOLO\",\"maxUses\":1}").path("code").asText();
        String slug = "bos-" + suffix;
        register(code, slug, "bos" + suffix);

        JsonNode services = getPublicJson("/api/booking/services", slug);
        JsonNode staff = getPublicJson("/api/booking/staff", slug);

        assertThat(services.size()).as("hizmet menüsü şablondan ekilir").isPositive();
        assertThat(staff.size()).as("provisioning personel eklemez").isZero();
    }

    // ───────────────────────── yardımcılar ─────────────────────────

    private JsonNode createInvite(String body) throws Exception {
        String response = mockMvc.perform(post("/api/platform/invites")
                        .with(authentication(platformAdmin())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data");
    }

    private JsonNode register(String code, String slug, String username) throws Exception {
        String response = mockMvc.perform(registerRequest(code, slug, username))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder registerRequest(
            String code, String slug, String username) {
        return post("/api/onboarding/register")
                .header("X-Forwarded-For", IP_HEADER_VALUE)
                .with(request -> {
                    request.setRemoteAddr(IP_HEADER_VALUE);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "inviteCode": "%s",
                          "organizationName": "Davet Org",
                          "salonName": "Davet Salon",
                          "salonSlug": "%s",
                          "adminUsername": "%s",
                          "adminPassword": "password123"
                        }
                        """.formatted(code, slug, username));
    }

    private JsonNode getPublicJson(String path, String slug) throws Exception {
        String response = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)
                                .header("X-Salon-Slug", slug))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data");
    }

    private UsernamePasswordAuthenticationToken platformAdmin() {
        AuthenticatedUser user = new AuthenticatedUser(
                platformAdminId, "platform-admin-" + suffix, "", true, UserRole.PLATFORM_ADMIN,
                null, null, null, null, false, 0,
                List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN")));
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }
}
