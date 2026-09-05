package com.gscrm.flow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gscrm.model.enums.UserRole;
import com.gscrm.repository.AppointmentRepository;
import com.gscrm.repository.UserRepository;
import com.gscrm.security.AuthenticatedUser;
import com.gscrm.security.RateLimitFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pilot müşterinin izlediği yolun tamamı — davet kodundan ilk randevuya.
 *
 * <p>Neden tek bir büyük test: 2026-09-04/05'te ana kullanıcı akışlarında altı hata
 * çıktı ve hiçbirini o günkü 245 test yakalamadı. Altısı da <b>katmanlar arası
 * bağlantı</b> hatasıydı — şablona geçmeyen bir değişken, yanlış yönlendirme hedefi,
 * sabit kodlanmış bir meta etiketi. Her katman kendi içinde doğruydu; kırık olan
 * aralarındaki bağlantıydı. Birim testi bu sınıfı göremez, sayfa render testi de
 * "açılıyor mu" sorusundan öteye gitmez. Yalnızca akışı baştan sona yürüyen bir test
 * görür.
 *
 * <p>Özellikle {@link #pilotCustomerCanGoFromInviteCodeToFirstAppointment()} içindeki
 * <b>gerçek form girişi</b> adımı kritik: kimlik doğrulamayı taklit etmek yerine
 * {@code POST /login} yapıp yönlendirmeyi takip eder. Salonsuz platform yöneticisinin
 * sonsuz {@code /login} döngüsüne düştüğü hata tam bu boşluktan kaçmıştı — testlerin
 * hepsi kimliği hazır kabul ediyordu.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Pilot müşteri uçtan uca akışı")
class PilotOnboardingFlowIT {

    private static final String ADMIN_PASSWORD = "Pilot-Parola-2026";
    private static final String NEW_PASSWORD = "Yeni-Parola-2026";
    /** Bot tuzağı 3 sn'den hızlı doldurulan formu reddediyor. */
    private static final long HUMAN_FILL_MILLIS = 12_000L;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RateLimitFilter rateLimitFilter;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private UserRepository userRepository;

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);

    @BeforeEach
    void resetRateLimits() {
        // Kayıt ucu 5/dk ile sınırlı; testler aynı IP kovasını paylaşıyor.
        rateLimitFilter.reset();
    }

    @Test
    @DisplayName("davet kodu → kayıt → giriş → parola → kurulum → hizmet/personel → randevu")
    void pilotCustomerCanGoFromInviteCodeToFirstAppointment() throws Exception {
        // ─── 1. Platform panelinden davet kodu üret ───
        String code = createInviteCode();
        assertThat(code).isNotBlank();

        // ─── 2. Müşteri kodu bozdurup kiracısını açar ───
        String username = "pilot-" + suffix;
        String salonSlug = "pilot-salon-" + suffix;
        JsonNode tenant = register(code, username, salonSlug);

        long salonId = tenant.path("salonId").asLong();
        assertThat(salonId).isPositive();
        assertThat(tenant.path("salonSlug").asText()).isEqualTo(salonSlug);

        // ─── 3. GERÇEK form girişi — kimliği taklit etmiyoruz ───
        // Yeni kiracının admin'i mustChangePassword=true ile açılır: hedef
        // /change-password olmalı. "/" olsaydı TenantFilter kullanıcıyı /login'e
        // geri atardı ve giriş sonuçsuz kalırdı.
        MvcResult login = mockMvc.perform(formLogin("/login").user(username).password(ADMIN_PASSWORD))
                .andExpect(authenticated())
                .andExpect(redirectedUrl("/change-password"))
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).as("giriş bir oturum açmalı").isNotNull();

        // Yönlendirilen sayfa gerçekten açılmalı.
        mockMvc.perform(get("/change-password").session(session))
                .andExpect(status().isOk());

        // ─── 4. Parola değişimi ve sunucunun söylediği hedef ───
        String nextUrl = changePassword(session);
        // Kurulumu bitmemiş kiracı sihirbaza gitmeli; "/" takvime düşürürdü.
        assertThat(nextUrl).isEqualTo("/onboarding/setup");

        // Parola değişince oturum yenileniyor: yeni parolayla tekrar gir.
        MvcResult relogin = mockMvc.perform(formLogin("/login").user(username).password(NEW_PASSWORD))
                .andExpect(authenticated())
                .andExpect(redirectedUrl("/onboarding/setup"))
                .andReturn();
        session = (MockHttpSession) relogin.getRequest().getSession(false);

        mockMvc.perform(get(nextUrl).session(session)).andExpect(status().isOk());

        // ─── 5. Hizmet ve personel ekle ───
        long serviceId = createService(session, salonSlug);
        long staffId = createStaff(session, salonSlug);

        // ─── 6. Müşterinin göreceği randevu sayfası anonim açılmalı ───
        mockMvc.perform(get("/" + salonSlug)).andExpect(status().isOk());

        // Hizmet ve personel herkese açık uçta görünmeli — booking sayfası bunları çizer.
        assertThat(publicList("/api/booking/services", salonSlug)).isNotEmpty();
        assertThat(publicList("/api/booking/staff", salonSlug)).isNotEmpty();

        // ─── 7. Ziyaretçi randevu talebi oluşturur ───
        LocalDateTime slot = nextWeekday().atTime(LocalTime.of(11, 0));
        bookPublicly(salonSlug, staffId, serviceId, slot);

        // ─── 8. Talep salonun takvimine düşmeli ───
        assertThat(appointmentRepository.findAll().stream()
                .filter(a -> salonId == a.getSalonId())
                .filter(a -> "Pilot Müşteri".equals(a.getCustomerName()))
                .toList())
                .as("public sayfadan gelen randevu salonun takviminde görünmeli")
                .hasSize(1);
    }

    // ─── Adımlar ───

    private String createInviteCode() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/platform/invites")
                        .with(authentication(platformAdmin()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"PILOT\",\"planCode\":\"SOLO\",\"maxUses\":1,\"trialDays\":90}"))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).path("data").path("code").asText();
    }

    private JsonNode register(String code, String username, String salonSlug) throws Exception {
        Map<String, Object> body = Map.of(
                "inviteCode", code,
                "organizationName", "Pilot Organizasyon " + suffix,
                "salonName", "Pilot Salon " + suffix,
                "salonSlug", salonSlug,
                "contactEmail", "pilot-" + suffix + "@example.test",
                "adminUsername", username,
                "adminPassword", ADMIN_PASSWORD);

        MvcResult result = mockMvc.perform(post("/api/onboarding/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).path("data");
    }

    private String changePassword(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/change-password")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", ADMIN_PASSWORD,
                                "newPassword", NEW_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).path("data").path("nextUrl").asText();
    }

    private long createService(MockHttpSession session, String slug) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/services")
                        .session(session)
                        .with(csrf())
                        .header("X-Salon-Slug", slug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Saç Kesim\",\"durationMinutes\":45,"
                                + "\"basePrice\":300,\"category\":\"HAIR\",\"active\":true}"))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).path("data").path("id").asLong();
    }

    private long createStaff(MockHttpSession session, String slug) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/staff")
                        .session(session)
                        .with(csrf())
                        .header("X-Salon-Slug", slug)
                        .contentType(MediaType.APPLICATION_JSON)
                        // Ad benzersiz olmalı: personel adından üretilen giriş
                        // kullanıcı adı ("ayse.yilmaz" gibi) global bir havuzdan
                        // geliyor ve sabit bir ad, o adı bekleyen başka testleri
                        // sırayla bozuyor.
                        .content("{\"name\":\"Pilot Uzman " + suffix + "\","
                                + "\"phone\":\"0532 100 0001\","
                                + "\"email\":\"uzman-" + suffix + "@example.test\","
                                + "\"role\":\"SPECIALIST\",\"active\":true}"))
                .andExpect(status().isOk())
                .andReturn();
        // Yanıt personeli ve — istenmişse — açılan giriş hesabını birlikte taşır.
        return json(result).path("data").path("staff").path("id").asLong();
    }

    private JsonNode publicList(String path, String slug) throws Exception {
        MvcResult result = mockMvc.perform(get(path).header("X-Salon-Slug", slug))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).path("data");
    }

    private void bookPublicly(String slug, long staffId, long serviceId, LocalDateTime slot)
            throws Exception {
        Map<String, Object> body = Map.of(
                "customerName", "Pilot Müşteri",
                "customerPhone", "0555 111 2233",
                "staffId", staffId,
                "serviceId", serviceId,
                "startTime", slot.toString(),
                // Bot tuzağı: boş honeypot + insan hızında doldurma süresi.
                "website", "",
                "elapsedMs", HUMAN_FILL_MILLIS);

        MvcResult result = mockMvc.perform(post("/api/booking/request")
                        .header("X-Salon-Slug", slug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn();

        assertThat(result.getResponse().getStatus())
                .as("randevu talebi kabul edilmeli — yanıt: %s",
                        result.getResponse().getContentAsString())
                .isEqualTo(200);
    }

    // ─── Yardımcılar ───

    /** Pazar günü personel çalışmıyor; randevu hafta içine düşsün. */
    private LocalDate nextWeekday() {
        LocalDate date = LocalDate.now().plusDays(1);
        while (date.getDayOfWeek().getValue() >= 6) {
            date = date.plusDays(1);
        }
        return date;
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /**
     * Seed'lenmiş gerçek platform yöneticisi.
     *
     * <p>Uydurma bir kimlik yetmiyor: {@code invite_code.created_by} users(id)'ye
     * foreign key ile bağlı, var olmayan bir kimlik kaydı 409'a düşürüyor.
     */
    private UsernamePasswordAuthenticationToken platformAdmin() {
        Long id = userRepository.findByUsername("platform_admin")
                .map(com.gscrm.model.User::getId)
                .orElseThrow(() -> new IllegalStateException("platform_admin seed'i bulunamadı"));
        AuthenticatedUser user = new AuthenticatedUser(
                id, "platform_admin", "", true, UserRole.PLATFORM_ADMIN,
                null, null, null, null, false, 0,
                List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN")));
        return UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
    }
}
