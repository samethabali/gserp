package com.gscrm.ui;

import com.gscrm.model.Organization;
import com.gscrm.model.Salon;
import com.gscrm.model.enums.OrganizationType;
import com.gscrm.model.enums.UserRole;
import com.gscrm.repository.OrganizationRepository;
import com.gscrm.repository.SalonRepository;
import com.gscrm.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Arayüz sayfalarının gerçekten render edildiğini doğrular.
 *
 * <p>Thymeleaf hataları sessizdir: eksik bir değişken veya bozuk bir fragment
 * çağrısı ancak sayfa tarayıcıda açıldığında patlar; birim testleri bunu görmez.
 * Bu test her sayfayı yetkili bir rolle çağırıp HTTP 200 ve gerçek HTML gövdesi
 * döndüğünü kontrol eder — yani şablon zinciri baştan sona çalışıyor.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Arayüz sayfa render")
class UiPageRenderIT {

    private final String slug = "ui-render-" + java.util.UUID.randomUUID()
            .toString().substring(0, 8);

    @Autowired private MockMvc mockMvc;
    @Autowired private TransactionTemplate txTemplate;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private SalonRepository salonRepository;

    private Long orgId;
    private Long salonId;

    @BeforeEach
    void seedTenant() {
        txTemplate.executeWithoutResult(status -> {
            LocalDateTime now = LocalDateTime.now();
            Organization org = organizationRepository.save(Organization.builder()
                    .name("UI Render Org").type(OrganizationType.STANDALONE)
                    .active(true).loyaltyPolicy("SALON").createdAt(now).build());
            orgId = org.getId();
            Salon salon = salonRepository.save(Salon.builder()
                    .organizationId(orgId).slug(slug).name("UI Salon")
                    .timezone("Europe/Istanbul").active(true).createdAt(now).build());
            salonId = salon.getId();
        });
    }

    @ParameterizedTest(name = "{0} sayfası {1} rolüyle açılır")
    @CsvSource({
            "/dashboard,        BRANCH_MANAGER",
            "/services,         BRANCH_MANAGER",
            "/staff,            BRANCH_MANAGER",
            "/resources,        BRANCH_MANAGER",
            "/customers,        BRANCH_MANAGER",
            "/expenses,         BRANCH_MANAGER",
            "/products,         BRANCH_MANAGER",
            "/campaigns,        BRANCH_MANAGER",
            "/users,            BRANCH_MANAGER",
            "/settings,         BRANCH_MANAGER",
            "/settings/billing, BRANCH_MANAGER",
            "/audit,            BRANCH_MANAGER",
            "/change-password,  BRANCH_MANAGER",
            "/onboarding/setup, BRANCH_MANAGER",
            "/dashboard,        RECEPTIONIST",
            "/customers,        RECEPTIONIST",
            "/dashboard,        SPECIALIST",
            "/org/dashboard,    ORG_OWNER",
            "/platform/tenants, PLATFORM_ADMIN"
    })
    void authenticatedPagesRender(String path, String role) throws Exception {
        MvcResult result = mockMvc.perform(get(path.trim())
                        .with(authentication(authFor(UserRole.valueOf(role.trim()))))
                        .header("X-Salon-Slug", slug))
                .andReturn();

        String body = result.getResponse().getContentAsString();

        assertThat(result.getResponse().getStatus())
                .as("%s sayfası %s rolüyle 200 dönmeli", path, role)
                .isEqualTo(200);
        assertThat(body)
                .as("%s sayfası HTML gövdesi döndürmeli", path)
                .contains("<html");
        assertThat(result.getResolvedException())
                .as("%s sayfası şablon hatası vermemeli", path)
                .isNull();
    }

    /**
     * Menüdeki "Booking Sayfası" bağlantısı kanonik adrese gitmeli.
     *
     * <p>Sabit {@code /booking} yalnızca oturumdaki kiracıyla çözülüyordu: yeni
     * sekmede açılan adres paylaşılamıyor, oturumsuz açıldığında "işletme
     * belirtilmedi" hatası veriyordu.
     */
    @org.junit.jupiter.api.Test
    @DisplayName("menüdeki booking bağlantısı /{slug} adresine gider")
    void sidebarBookingLinkUsesCanonicalSlug() throws Exception {
        String body = mockMvc.perform(get("/dashboard")
                        .with(authentication(authFor(UserRole.BRANCH_MANAGER)))
                        .header("X-Salon-Slug", slug))
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .as("menü kanonik randevu adresini göstermeli")
                .contains("href=\"/" + slug + "\"")
                .doesNotContain("href=\"/booking\"");
    }

    @ParameterizedTest(name = "{0} herkese açık olarak render edilir")
    @CsvSource({"/login", "/booking", "/privacy", "/customer/login", "/customer/register", "/onboarding/wizard"})
    void publicPagesRender(String path) throws Exception {
        MvcResult result = mockMvc.perform(get(path).header("X-Salon-Slug", slug)).andReturn();

        assertThat(result.getResponse().getStatus())
                .as("%s herkese açık olmalı", path)
                .isEqualTo(200);
        assertThat(result.getResponse().getContentAsString())
                .as("%s HTML gövdesi döndürmeli", path)
                .contains("<html");
    }

    private UsernamePasswordAuthenticationToken authFor(UserRole role) {
        Long userSalonId = role == UserRole.PLATFORM_ADMIN ? null : salonId;
        Long userOrgId = role == UserRole.PLATFORM_ADMIN ? null : orgId;
        AuthenticatedUser user = new AuthenticatedUser(
                7001L, "ui-" + role.name().toLowerCase(), "", true, role,
                null, null, userSalonId, userOrgId, false, 0,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }
}
