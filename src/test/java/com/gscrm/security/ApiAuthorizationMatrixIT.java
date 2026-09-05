package com.gscrm.security;

import com.gscrm.model.Organization;
import com.gscrm.model.Salon;
import com.gscrm.model.enums.OrganizationType;
import com.gscrm.model.enums.UserRole;
import com.gscrm.repository.OrganizationRepository;
import com.gscrm.repository.SalonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Rol × uç yetki matrisi.
 *
 * <p>{@code SecurityConfig} 27 rol kuralı tanımlıyordu ama testlerde 403 bekleyen
 * yalnızca beş kontrol vardı. Yani "resepsiyonist kullanıcı yönetimine girebiliyor
 * mu", "uzman masrafları görebiliyor mu" gibi sorular sistematik olarak
 * sorulmuyordu; yanlış yazılmış ya da yeni uç eklenirken unutulmuş bir kural
 * sessizce geçerdi.
 *
 * <p>Burada her uç için <b>izinli roller</b> beyan ediliyor ve altı rolün hepsi tek
 * tek deneniyor. İzinli rol 403 almamalı, izinsiz rol <b>mutlaka</b> 403 almalı.
 *
 * <p>İzinli tarafta 200 değil "403 değil" aranıyor: uç, veri yokluğundan 400 ya da
 * 404 dönebilir ve bu testin konusu yetkilendirme, iş mantığı değil. İzinsiz tarafta
 * ise kesin 403 aranıyor — orada belirsizliğe yer yok.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Yetki matrisi")
class ApiAuthorizationMatrixIT {

    /** Yönetim. */
    private static final Set<UserRole> MGMT = Set.of(
            UserRole.ADMIN, UserRole.BRANCH_MANAGER, UserRole.ORG_OWNER, UserRole.PLATFORM_ADMIN);
    /** Yönetim + resepsiyon. */
    private static final Set<UserRole> MGMT_RECEPTION = Set.of(
            UserRole.ADMIN, UserRole.BRANCH_MANAGER, UserRole.ORG_OWNER, UserRole.PLATFORM_ADMIN,
            UserRole.RECEPTIONIST);
    /** Personelin okuyabildiği her şey. */
    private static final Set<UserRole> STAFF_READ = Set.of(
            UserRole.ADMIN, UserRole.BRANCH_MANAGER, UserRole.ORG_OWNER, UserRole.PLATFORM_ADMIN,
            UserRole.RECEPTIONIST, UserRole.SPECIALIST);

    /** Matriste denenen roller. CUSTOMER portal tarafına ait, burada kapsam dışı. */
    private static final List<UserRole> ROLES = List.of(
            UserRole.PLATFORM_ADMIN, UserRole.ORG_OWNER, UserRole.BRANCH_MANAGER,
            UserRole.ADMIN, UserRole.RECEPTIONIST, UserRole.SPECIALIST);

    private record Rule(HttpMethod method, String path, Set<UserRole> allowed) {
        @Override
        public String toString() {
            return method + " " + path;
        }
    }

    /** SecurityConfig'teki kuralların birebir karşılığı. */
    private static List<Rule> rules() {
        return List.of(
                new Rule(HttpMethod.GET, "/api/platform/tenants", Set.of(UserRole.PLATFORM_ADMIN)),
                new Rule(HttpMethod.GET, "/api/org/salons",
                        Set.of(UserRole.ORG_OWNER, UserRole.PLATFORM_ADMIN)),
                new Rule(HttpMethod.GET, "/api/settings", MGMT),
                new Rule(HttpMethod.GET, "/api/audit/events", MGMT),
                new Rule(HttpMethod.GET, "/api/inventory/stock", MGMT),
                new Rule(HttpMethod.GET, "/api/users", MGMT),
                new Rule(HttpMethod.GET, "/api/campaigns", MGMT_RECEPTION),
                new Rule(HttpMethod.GET, "/api/payments", MGMT_RECEPTION),
                new Rule(HttpMethod.GET, "/api/expenses", MGMT_RECEPTION),
                new Rule(HttpMethod.GET, "/api/products", MGMT_RECEPTION),
                new Rule(HttpMethod.GET, "/api/waitlist", MGMT_RECEPTION),
                new Rule(HttpMethod.GET, "/api/customers", MGMT_RECEPTION),
                new Rule(HttpMethod.GET, "/api/dashboard/summary", STAFF_READ),
                new Rule(HttpMethod.GET, "/api/appointments", STAFF_READ),
                // Aynı yolun okuması personele, yazması yönetime açık.
                new Rule(HttpMethod.GET, "/api/staff", STAFF_READ),
                new Rule(HttpMethod.POST, "/api/staff", MGMT),
                new Rule(HttpMethod.GET, "/api/services", STAFF_READ),
                new Rule(HttpMethod.POST, "/api/services", MGMT),
                new Rule(HttpMethod.GET, "/api/resources", STAFF_READ),
                new Rule(HttpMethod.POST, "/api/resources", MGMT));
    }

    static Stream<Arguments> matrix() {
        List<Arguments> rows = new ArrayList<>();
        for (Rule rule : rules()) {
            for (UserRole role : ROLES) {
                rows.add(Arguments.of(rule, role, rule.allowed().contains(role)));
            }
        }
        return rows.stream();
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private TransactionTemplate txTemplate;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private SalonRepository salonRepository;

    private final String slug = "authz-" + java.util.UUID.randomUUID().toString().substring(0, 8);
    private Long orgId;
    private Long salonId;

    @BeforeEach
    void seedTenant() {
        if (salonId != null) {
            return;
        }
        txTemplate.executeWithoutResult(status -> {
            LocalDateTime now = LocalDateTime.now();
            Organization org = organizationRepository.save(Organization.builder()
                    .name("Yetki Org").type(OrganizationType.STANDALONE)
                    .active(true).loyaltyPolicy("SALON").createdAt(now).build());
            orgId = org.getId();
            salonId = salonRepository.save(Salon.builder()
                    .organizationId(orgId).slug(slug).name("Yetki Salonu")
                    .timezone("Europe/Istanbul").active(true).createdAt(now).build()).getId();
        });
    }

    @ParameterizedTest(name = "{0} — {1} — izinli={2}")
    @MethodSource("matrix")
    void enforcesRoleMatrix(Rule rule, UserRole role, boolean allowed) throws Exception {
        MockHttpServletRequestBuilder request = rule.method() == HttpMethod.POST
                ? post(rule.path()).with(csrf()).contentType("application/json").content("{}")
                : get(rule.path());

        MvcResult result = mockMvc.perform(request
                        .with(authentication(authFor(role)))
                        .header("X-Salon-Slug", slug))
                .andReturn();

        int status = result.getResponse().getStatus();

        if (allowed) {
            assertThat(status)
                    .as("%s — %s rolü yetkili olmalı ama 403 aldı", rule, role)
                    .isNotEqualTo(403);
        } else {
            assertThat(status)
                    .as("%s — %s rolü reddedilmeli, dönen: %s", rule, role, status)
                    .isEqualTo(403);
        }
    }

    /**
     * Personel kaydına bağlanmamış uzman hesabı reddedilmeli.
     *
     * <p>Uzman yalnızca kendi randevularını görebiliyor; bu kapsamlama
     * {@code staffId} üzerinden yapılıyor. Bağ yoksa "kendi randevusu" tanımsız
     * kalır ve uç sessizce herkesin verisini döndürmek yerine erişimi kesiyor.
     * Matris testi uzmanı bağlı kabul ettiği için bu kenar durum ayrıca sabitleniyor.
     */
    @org.junit.jupiter.api.Test
    @DisplayName("personel kaydına bağlı olmayan uzman randevuları göremez")
    void specialistWithoutStaffRecordIsDenied() throws Exception {
        AuthenticatedUser unlinked = new AuthenticatedUser(
                9002L, "authz-unlinked", "", true, UserRole.SPECIALIST,
                null, null, salonId, orgId, false, 0,
                List.of(new SimpleGrantedAuthority("ROLE_SPECIALIST")));

        mockMvc.perform(get("/api/appointments")
                        .with(authentication(UsernamePasswordAuthenticationToken.authenticated(
                                unlinked, null, unlinked.getAuthorities())))
                        .header("X-Salon-Slug", slug))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isForbidden());
    }

    private UsernamePasswordAuthenticationToken authFor(UserRole role) {
        // Platform yöneticisi salonsuz olabiliyor; diğer roller kiracıya bağlı.
        Long userSalonId = role == UserRole.PLATFORM_ADMIN ? null : salonId;
        Long userOrgId = role == UserRole.PLATFORM_ADMIN ? null : orgId;
        // Uzman kendi randevularına staffId üzerinden kapsamlanıyor; bağ yoksa uç
        // erişimi kesiyor. Matris rol kurallarını ölçtüğü için uzman bağlı kabul
        // edilir; bağsız hâl specialistWithoutStaffRecordIsDenied ile ayrıca test edilir.
        Long staffId = role == UserRole.SPECIALIST ? 9101L : null;
        AuthenticatedUser user = new AuthenticatedUser(
                9001L, "authz-" + role.name().toLowerCase(), "", true, role,
                staffId, null, userSalonId, userOrgId, false, 0,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        return UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
    }
}
