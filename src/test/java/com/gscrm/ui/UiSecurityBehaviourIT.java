package com.gscrm.ui;

import com.gscrm.model.Organization;
import com.gscrm.model.Salon;
import com.gscrm.model.User;
import com.gscrm.model.enums.OrganizationType;
import com.gscrm.model.enums.UserRole;
import com.gscrm.repository.OrganizationRepository;
import com.gscrm.repository.SalonRepository;
import com.gscrm.repository.UserRepository;
import com.gscrm.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Kimlik ve oturum korumalarının gerçekten uygulandığını doğrular.
 *
 * <p>Bu testlerin her biri, denetimde tespit edilen somut bir boşluğa karşılık gelir:
 * devre dışı bırakılan kullanıcının token'ıyla çalışmaya devam etmesi, parola
 * değişiminin eski oturumları kapatmaması ve giriş ucunda hız sınırı bulunmaması.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Kimlik ve oturum davranışı")
class UiSecurityBehaviourIT {

    private final String slug = "ui-sec-" + UUID.randomUUID().toString().substring(0, 8);

    @Autowired private MockMvc mockMvc;
    @Autowired private TransactionTemplate txTemplate;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private SalonRepository salonRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserDetailsService userDetailsService;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private com.gscrm.security.RateLimitFilter rateLimitFilter;

    private Long salonId;
    private String username;

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        rateLimitFilter.reset();
    }

    @BeforeEach
    void seed() {
        username = "sec-" + UUID.randomUUID().toString().substring(0, 8);
        txTemplate.executeWithoutResult(status -> {
            LocalDateTime now = LocalDateTime.now();
            Organization org = organizationRepository.save(Organization.builder()
                    .name("Guvenlik Org").type(OrganizationType.STANDALONE)
                    .active(true).loyaltyPolicy("SALON").createdAt(now).build());
            salonId = salonRepository.save(Salon.builder()
                    .organizationId(org.getId()).slug(slug).name("Guvenlik Salonu")
                    .timezone("Europe/Istanbul").active(true).createdAt(now).build()).getId();
            userRepository.save(User.builder()
                    .salonId(salonId).organizationId(org.getId())
                    .username(username).passwordHash(passwordEncoder.encode("Parola12345"))
                    .role(UserRole.BRANCH_MANAGER).enabled(true)
                    .mustChangePassword(false).tokenVersion(0).createdAt(now).build());
        });
    }

    @Test
    @DisplayName("Devre dışı bırakılan kullanıcının token'ı kabul edilmez")
    void disabledUserTokenIsRejected() throws Exception {
        String token = issueTokenWithTenantContext();

        assertThat(callDashboardWith(token))
                .as("Aktif kullanıcı erişebilmeli").isEqualTo(200);

        txTemplate.executeWithoutResult(status -> {
            User user = userRepository.findBySalonIdAndUsername(salonId, username).orElseThrow();
            user.setEnabled(false);
            userRepository.save(user);
        });

        assertThat(callDashboardWith(token))
                .as("Devre dışı bırakılan kullanıcı, elindeki token'la erişmeye devam edemez")
                .isEqualTo(401);
    }

    @Test
    @DisplayName("Token sürümü artınca eski token geçersizleşir")
    void tokenVersionBumpInvalidatesOldToken() throws Exception {
        String token = issueTokenWithTenantContext();
        assertThat(callDashboardWith(token)).isEqualTo(200);

        // Parola değişimi / yönetici sıfırlaması bu sayacı artırır.
        txTemplate.executeWithoutResult(status -> {
            User user = userRepository.findBySalonIdAndUsername(salonId, username).orElseThrow();
            user.setTokenVersion(user.getTokenVersion() + 1);
            userRepository.save(user);
        });

        assertThat(callDashboardWith(token))
                .as("Parola değişiminden önce üretilmiş token reddedilmeli")
                .isEqualTo(401);
    }

    @Test
    @DisplayName("Giriş ucu kaba kuvvete karşı hız sınırlı")
    void loginIsRateLimited() throws Exception {
        int tooManyRequests = 0;
        for (int attempt = 0; attempt < 25; attempt++) {
            MvcResult result = mockMvc.perform(post("/api/auth/login")
                            .header("X-Salon-Slug", slug)
                            .contentType("application/json")
                            .content("{\"username\":\"" + username + "\",\"password\":\"yanlis\"}"))
                    .andReturn();
            if (result.getResponse().getStatus() == 429) {
                tooManyRequests++;
            }
        }
        assertThat(tooManyRequests)
                .as("Ardışık başarısız girişler bir noktada 429 ile durdurulmalı")
                .isPositive();
    }

    @Test
    @DisplayName("Güvenlik başlıkları yanıtta bulunur")
    void securityHeadersArePresent() throws Exception {
        MvcResult result = mockMvc.perform(get("/login").header("X-Salon-Slug", slug)).andReturn();

        assertThat(result.getResponse().getHeader("Content-Security-Policy"))
                .as("CSP başlığı tanımlı olmalı").isNotBlank();
        assertThat(result.getResponse().getHeader("Referrer-Policy"))
                .as("Referrer-Policy tanımlı olmalı").isNotBlank();
        assertThat(result.getResponse().getHeader("X-Frame-Options"))
                .as("Çerçeveleme engellenmeli").isEqualTo("DENY");
    }

    // ─────────────────────────── yardımcılar ───────────────────────────

    /**
     * Token üretimi kullanıcıyı tenant bağlamıyla yükler; {@code CustomUserDetailsService}
     * salon bağlamına göre arama yapar.
     */
    private String issueTokenWithTenantContext() {
        com.gscrm.tenant.TenantContext.setSalonId(salonId);
        try {
            UserDetails user = userDetailsService.loadUserByUsername(username);
            return jwtService.generateToken(user);
        } finally {
            com.gscrm.tenant.TenantContext.clear();
        }
    }

    private int callDashboardWith(String token) throws Exception {
        return mockMvc.perform(get("/api/dashboard?date=" + java.time.LocalDate.now())
                        .header("Authorization", "Bearer " + token)
                        .header("X-Salon-Slug", slug))
                .andReturn().getResponse().getStatus();
    }
}
