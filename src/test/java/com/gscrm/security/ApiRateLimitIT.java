package com.gscrm.security;

import com.gscrm.model.Organization;
import com.gscrm.model.Salon;
import com.gscrm.model.enums.OrganizationType;
import com.gscrm.model.enums.UserRole;
import com.gscrm.repository.OrganizationRepository;
import com.gscrm.repository.SalonRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Kimlikli API uçlarındaki genel hız sınırı.
 *
 * <p>Bu uçlarda hiç sınır yoktu: ele geçirilmiş bir oturum ya da kaçak bir istemci
 * döngüsü, iki çekirdekli ve on iki konteyner paylaşan makineyi doğrultabilirdi.
 *
 * <p>Testin asıl derdi sınırın <b>çalışması</b> değil, <b>normal kullanımı
 * engellememesi</b>. Bir sınır kullanıcıyı takıyorsa, çözdüğü sorundan büyük bir
 * sorun yaratmış olur; bu yüzden iki yön de sabitleniyor.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("API hız sınırı")
class ApiRateLimitIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private RateLimitFilter rateLimitFilter;
    @Autowired private TransactionTemplate txTemplate;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private SalonRepository salonRepository;

    private final String slug = "rate-" + java.util.UUID.randomUUID().toString().substring(0, 8);
    private Long orgId;
    private Long salonId;

    /**
     * Sayaçları testten <b>sonra</b> da temizle.
     *
     * <p>Buradaki testler bilerek bir bütçeyi tüketiyor. Sayaç haritası
     * uygulama bağlamıyla birlikte diğer test sınıflarına da taşındığı ve
     * hepsi aynı istemci IP'sinden geldiği için, temizlenmeyen bir kova
     * sonraki sınıfın ilk isteğine 429 döndürüyordu — ürün hatası olmadığı
     * hâlde kırılan iki test.
     */
    @AfterEach
    void clearCounters() {
        rateLimitFilter.reset();
    }

    @BeforeEach
    void seedAndReset() {
        rateLimitFilter.reset();
        if (salonId != null) {
            return;
        }
        txTemplate.executeWithoutResult(status -> {
            LocalDateTime now = LocalDateTime.now();
            Organization org = organizationRepository.save(Organization.builder()
                    .name("Hız Org").type(OrganizationType.STANDALONE)
                    .active(true).loyaltyPolicy("SALON").createdAt(now).build());
            orgId = org.getId();
            salonId = salonRepository.save(Salon.builder()
                    .organizationId(orgId).slug(slug).name("Hız Salonu")
                    .timezone("Europe/Istanbul").active(true).createdAt(now).build()).getId();
        });
    }

    @Test
    @DisplayName("yoğun ama gerçekçi kullanım sınıra takılmaz")
    void realisticUsageIsNotThrottled() throws Exception {
        MockHttpSession session = new MockHttpSession();

        // Yoğun bir resepsiyonistin dakikalık hacminin üstü: takvimi 120 kez çevirmek.
        for (int i = 0; i < 120; i++) {
            int status = mockMvc.perform(get("/api/appointments")
                            .session(session)
                            .with(authentication(authFor(UserRole.BRANCH_MANAGER)))
                            .header("X-Salon-Slug", slug))
                    .andReturn().getResponse().getStatus();

            assertThat(status)
                    .as("%d. istekte sınıra takıldı — normal kullanım engellenmemeli", i + 1)
                    .isNotEqualTo(429);
        }
    }

    @Test
    @DisplayName("kaçak döngü 429 ile durdurulur")
    void runawayLoopIsStopped() throws Exception {
        MockHttpSession session = new MockHttpSession();
        boolean throttled = false;

        // Sınır 300/dk; 350 istek onu aşmalı.
        for (int i = 0; i < 350 && !throttled; i++) {
            throttled = mockMvc.perform(get("/api/appointments")
                            .session(session)
                            .with(authentication(authFor(UserRole.BRANCH_MANAGER)))
                            .header("X-Salon-Slug", slug))
                    .andReturn().getResponse().getStatus() == 429;
        }

        assertThat(throttled).as("kaçak döngü durdurulmalı").isTrue();
    }

    /**
     * Aynı ofisten çalışan iki personel birbirini kilitlememeli.
     *
     * <p>Kova IP'ye dayansaydı tek NAT arkasındaki beş kişilik ekip aynı bütçeyi
     * paylaşır ve biri yoğun çalışırken diğerleri takılırdı.
     */
    @Test
    @DisplayName("aynı IP'deki ikinci personel ayrı bütçeye sahip")
    void separateSessionsGetSeparateBudgets() throws Exception {
        MockHttpSession busy = new MockHttpSession();
        for (int i = 0; i < 350; i++) {
            mockMvc.perform(get("/api/appointments")
                    .session(busy)
                    .with(authentication(authFor(UserRole.BRANCH_MANAGER)))
                    .header("X-Salon-Slug", slug));
        }

        MockHttpSession fresh = new MockHttpSession();
        int status = mockMvc.perform(get("/api/appointments")
                        .session(fresh)
                        .with(authentication(authFor(UserRole.RECEPTIONIST)))
                        .header("X-Salon-Slug", slug))
                .andReturn().getResponse().getStatus();

        assertThat(status)
                .as("meslektaşının yoğunluğu bu kullanıcıyı engellememeli")
                .isNotEqualTo(429);
    }

    // ─────────────────── randevu sayfasının okuma uçları ───────────────────

    /**
     * Randevu sayfasında gezinmek sınıra takılmamalı.
     *
     * <p>Bu uçlar müşterinin gördüğü tek yüzey; burada bir 429 doğrudan kaybedilmiş
     * randevu demek.
     */
    @Test
    @DisplayName("randevu sayfasında normal gezinme sınıra takılmaz")
    void bookingBrowsingIsNotThrottled() throws Exception {
        MockHttpSession visitor = new MockHttpSession();

        // Sayfa açılışı + on beş farklı gün denemesi: gerçekçi bir üst sınır.
        for (int i = 0; i < 60; i++) {
            int status = mockMvc.perform(get("/api/booking/services")
                            .session(visitor)
                            .header("X-Salon-Slug", slug))
                    .andReturn().getResponse().getStatus();

            assertThat(status)
                    .as("%d. istekte sınıra takıldı — randevu sayfası akıcı olmalı", i + 1)
                    .isNotEqualTo(429);
        }
    }

    /**
     * Aynı IP'den gelen iki müşteri birbirini kilitlememeli.
     *
     * <p>Kova IP'ye bağlıydı. Türkiye'de mobil operatörler CGNAT kullanıyor: aynı
     * operatörden gelen onlarca müşteri sunucuya <b>tek bir IP</b> olarak görünür.
     * Salonun bağlantısı Instagram'da paylaşıldığında bu müşteriler tek bütçeyi
     * paylaşır ve bir kısmı "çok fazla deneme" duvarına çarpardı.
     */
    @Test
    @DisplayName("aynı IP'deki ikinci müşteri ayrı bütçeye sahip")
    void bookingReadsAreScopedToVisitorNotIp() throws Exception {
        MockHttpSession busy = new MockHttpSession();
        for (int i = 0; i < 200; i++) {
            mockMvc.perform(get("/api/booking/services")
                    .session(busy)
                    .header("X-Salon-Slug", slug));
        }

        MockHttpSession arriving = new MockHttpSession();
        int status = mockMvc.perform(get("/api/booking/services")
                        .session(arriving)
                        .header("X-Salon-Slug", slug))
                .andReturn().getResponse().getStatus();

        assertThat(status)
                .as("komşusunun yoğunluğu yeni gelen müşteriyi engellememeli")
                .isNotEqualTo(429);
    }

    /**
     * Oturum tutmayan istemci korumasız kalmamalı.
     *
     * <p>Ziyaretçi bazlı sayaç, çerez tutmayan bir betiğe sınırsız erişim vermek
     * anlamına gelseydi koruma tamamen kalkardı. Oturumu olmayan istek IP kovasına
     * düşer.
     */
    @Test
    @DisplayName("çerez tutmayan istemci IP kovasına düşer")
    void bookingReadsWithoutSessionFallBackToIp() throws Exception {
        boolean throttled = false;
        for (int i = 0; i < 300 && !throttled; i++) {
            throttled = mockMvc.perform(get("/api/booking/services")
                            .header("X-Salon-Slug", slug))
                    .andReturn().getResponse().getStatus() == 429;
        }

        assertThat(throttled).as("oturumsuz kaçak istemci durdurulmalı").isTrue();
    }

    private UsernamePasswordAuthenticationToken authFor(UserRole role) {
        AuthenticatedUser user = new AuthenticatedUser(
                9301L, "rate-" + role.name().toLowerCase(), "", true, role,
                null, null, salonId, orgId, false, 0,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        return UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
    }
}
