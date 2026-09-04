package com.gscrm.controller;

import com.gscrm.model.Organization;
import com.gscrm.model.Salon;
import com.gscrm.model.enums.OrganizationType;
import com.gscrm.repository.OrganizationRepository;
import com.gscrm.repository.SalonRepository;
import com.gscrm.security.RateLimitFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Müşteri portalı kimlik uçlarının kiracı bağlamı ve hız sınırı.
 *
 * <p>İki ayrı regresyon: (1) {@code /api/auth} bypass'ı {@code TenantFilter}'a
 * eklendiğinde {@code /api/auth/customer/**} de bypass'a düşmüş, uçlar ilk
 * satırdaki {@code requireSalonId()} çağrısında patlar hâle gelmişti. (2) Hız
 * sınırı yalnızca {@code /customer/login} sayfa yolunu tanıyordu, formlar ise
 * API ucuna POST ediyordu — müşteri parolası sınırsız denenebiliyordu.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Müşteri portalı kimlik uçları")
class CustomerPortalAuthIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private SalonRepository salonRepository;
    @Autowired private RateLimitFilter rateLimitFilter;
    @org.springframework.boot.test.mock.mockito.SpyBean private com.gscrm.service.ActivityEventService activityEventService;

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private String slug;

    @BeforeEach
    void seedTenant() {
        rateLimitFilter.reset();
        LocalDateTime now = LocalDateTime.now();
        Organization org = organizationRepository.save(Organization.builder()
                .name("Portal Org").type(OrganizationType.STANDALONE)
                .active(true).loyaltyPolicy("SALON").createdAt(now).build());
        slug = "portal-" + suffix;
        salonRepository.save(Salon.builder()
                .organizationId(org.getId()).slug(slug).name("Portal Salon")
                .timezone("Europe/Istanbul").active(true).createdAt(now).build());
    }

    @AfterEach
    void clearRateLimits() {
        rateLimitFilter.reset();
    }

    /**
     * Gerçek akış: müşteri {@code /b/{slug}} ile geliyor, o ziyaret salonu oturuma
     * yazıyor ve kayıt ucu kiracıyı oradan çözüyor.
     */
    @Test
    @DisplayName("kayıt, /b/{slug} ziyaretinden gelen oturum kiracısıyla çalışır")
    void registerResolvesTenantFromPublicSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(get("/b/" + slug).session(session)).andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/customer/register")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Ayse",
                                  "lastName": "Demir",
                                  "email": "portal-%s@example.com",
                                  "phone": "05551112233",
                                  "password": "password123"
                                }
                                """.formatted(suffix)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
                
        org.mockito.Mockito.verify(activityEventService).recordAuth(
                org.mockito.ArgumentMatchers.eq("LOGIN_SUCCESS"),
                org.mockito.ArgumentMatchers.eq("portal-" + suffix + "@example.com"),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.eq("SUCCESS"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    @DisplayName("giriş, açık slug ile kiracıyı çözer")
    void loginResolvesTenantFromExplicitSlug() throws Exception {
        // Kayıtsız e-posta: 401 bekleniyor. Önemli olan bağlamın çözülmesi —
        // bypass hatası döndüğünde bu uç 401 değil sunucu hatası veriyordu.
        mockMvc.perform(post("/api/auth/customer/login")
                        .header("X-Salon-Slug", slug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"yok-%s@example.com\",\"password\":\"password123\"}"
                                .formatted(suffix)))
                .andExpect(status().isUnauthorized());
                
        org.mockito.Mockito.verify(activityEventService).recordAuth(
                org.mockito.ArgumentMatchers.eq("LOGIN_FAILED"),
                org.mockito.ArgumentMatchers.eq("yok-" + suffix + "@example.com"),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq("DENIED"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    /**
     * Kiracı çözülemediğinde uç anlamlı bir istemci hatası dönmeli; bypass
     * yüzünden bağlam hiç kurulmadığında dönen sunucu hatası bir regresyondu.
     */
    @Test
    @DisplayName("kiracı belirtilmezse 4xx döner, sunucu hatası değil")
    void loginWithoutTenantReturnsClientError() throws Exception {
        mockMvc.perform(post("/api/auth/customer/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"yok@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("müşteri girişi hız sınırına tabidir")
    void customerLoginIsRateLimited() throws Exception {
        for (int i = 0; i < 8; i++) {
            mockMvc.perform(loginAttempt()).andExpect(status().isUnauthorized());
        }
        mockMvc.perform(loginAttempt()).andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("müşteri kaydı hız sınırına tabidir")
    void customerRegisterIsRateLimited() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(registerAttempt(i)).andExpect(status().is4xxClientError());
        }
        mockMvc.perform(registerAttempt(99)).andExpect(status().isTooManyRequests());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder loginAttempt() {
        return post("/api/auth/customer/login")
                .header("X-Salon-Slug", slug)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"brute-%s@example.com\",\"password\":\"wrong-password\"}"
                        .formatted(suffix));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder registerAttempt(int index) {
        // Geçersiz gövde: doğrulama 400 döner, böylece sayaç DB'ye yazmadan artar.
        return post("/api/auth/customer/register")
                .header("X-Salon-Slug", slug)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"\",\"email\":\"kotu-%d@example.com\",\"password\":\"kisa\"}"
                        .formatted(index));
    }
}
