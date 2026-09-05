package com.gscrm.perf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gscrm.model.Appointment;
import com.gscrm.model.Organization;
import com.gscrm.model.Salon;
import com.gscrm.model.ServiceDefinition;
import com.gscrm.model.Staff;
import com.gscrm.model.User;
import com.gscrm.model.WorkingHours;
import com.gscrm.model.enums.AppointmentStatus;
import com.gscrm.model.enums.OrganizationType;
import com.gscrm.model.enums.ServiceCategory;
import com.gscrm.model.enums.StaffRole;
import com.gscrm.model.enums.UserRole;
import com.gscrm.repository.AppointmentRepository;
import com.gscrm.repository.OrganizationRepository;
import com.gscrm.repository.SalonRepository;
import com.gscrm.repository.ServiceDefinitionRepository;
import com.gscrm.repository.StaffRepository;
import com.gscrm.repository.UserRepository;
import com.gscrm.repository.WorkingHoursRepository;
import com.gscrm.security.RateLimitFilter;
import com.zaxxer.hikari.HikariDataSource;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManagerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Yük ölçümü — 20 eşzamanlı ziyaretçi randevu sayfasında, 3 personel panelde.
 *
 * <p>Sistem bugüne kadar hiç yük altında görülmedi. Bilinmeyen üç şey vardı:
 * eşzamanlı ziyaretçi altında yanıt süresi ne oluyor, on bağlantılık havuz
 * doyuyor mu, ve tek bir "müsait saatler" isteği kaç SQL'e mal oluyor.
 *
 * <p><b>Neden CI'da koşmuyor:</b> {@code load} etiketi surefire'da dışlanıyor.
 * Paylaşımlı bir GitHub runner'ında ölçülen gecikme makinenin o anki yüküne
 * bağlıdır; böyle bir testi kırmızı/yeşil kapısı yapmak, ürün hatası olmadığı
 * hâlde düzenli olarak kırılan bir test demektir. Elle çalıştırmak için:
 *
 * <pre>mvn test -Dsurefire.excludedGroups= -Dtest=BookingLoadIT</pre>
 *
 * <p><b>Hız sınırı bilinçli olarak devre dışı:</b> ölçüm süresince
 * {@link RateLimitFilter} periyodik olarak sıfırlanıyor. Yük testinin amacı
 * uygulamanın kapasitesini ölçmek; sınıra takılan istekler DB'ye hiç inmediği
 * için ölçümü olduğundan iyi gösterirdi. Sınırın kendisi
 * {@code ApiRateLimitIT} ile ayrıca doğrulanıyor.
 *
 * <p><b>Kapsam dışı:</b> randevu <i>yazma</i> yükü. POST {@code /api/booking/request}
 * IP başına 10/dk ile sınırlı ve tek makineden gelen yük testi bunu anlamlı
 * biçimde ölçemez; yazma yolu kapasitesi ancak birden çok kaynaktan ölçülebilir.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("load")
@TestPropertySource(properties = {
        // Sorgu sayısını ölçebilmek için. Ölçüm öncesi/sonrası aynı ayarla
        // koştuğu için karşılaştırma adil kalıyor.
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@DisplayName("Yük ölçümü — randevu sayfası ve panel")
class BookingLoadIT {

    /** Eşzamanlı ziyaretçi sayısı — küçük bir salonun yoğun bir öğle saati. */
    private static final int VISITORS = 20;
    /** Panelde çalışan personel: bir resepsiyon, iki uzman. */
    private static final int PANEL_USERS = 3;
    private static final Duration RUN_FOR = Duration.ofSeconds(30);
    /** Ziyaretçinin ekrana bakma süresi; sürekli dönen bir döngü gerçekçi değil. */
    private static final long THINK_MILLIS = 250L;

    private static final String PANEL_PASSWORD = "Yuk-Testi-Parola-2026";

    @LocalServerPort private int port;

    @Autowired private TransactionTemplate txTemplate;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private RateLimitFilter rateLimitFilter;
    @Autowired private HikariDataSource dataSource;
    @Autowired private EntityManagerFactory entityManagerFactory;

    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private SalonRepository salonRepository;
    @Autowired private StaffRepository staffRepository;
    @Autowired private ServiceDefinitionRepository serviceRepository;
    @Autowired private WorkingHoursRepository workingHoursRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private UserRepository userRepository;

    private static final String SUFFIX = UUID.randomUUID().toString().substring(0, 8);
    private static final String SLUG = "yuk-" + SUFFIX;
    /** Yarın: geçmiş saat elenmesin, slot üretimi tam pencereyi dolaşsın. */
    private static final LocalDate TARGET_DAY = LocalDate.now().plusDays(1);

    private static boolean seeded;
    private static Long salonId;
    private static List<Long> staffIds;
    private static List<Long> serviceIds;
    private static final List<String> PANEL_USERNAMES = new ArrayList<>();

    private final ConcurrentLinkedQueue<Sample> samples = new ConcurrentLinkedQueue<>();

    private record Sample(String label, long millis, int status) {}

    @BeforeAll
    static void resetSeedFlag() {
        seeded = false;
    }

    // ─────────────────────────── ölçüm 1: tek isteğin SQL maliyeti ───────────────────────────

    /**
     * Tek bir "müsait saatler" isteği kaç SQL'e mal oluyor?
     *
     * <p>Bu sayı makineden bağımsız: yavaş bir makinede de hızlı bir makinede de
     * aynı çıkar. Yük testinin en taşınabilir çıktısı bu.
     */
    @Test
    @DisplayName("tek istek maliyeti — SQL sayısı")
    void sqlCostOfEachEndpoint() throws Exception {
        seedOnce();
        HttpClient client = newClient();
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        Map<String, String> endpoints = new LinkedHashMap<>();
        endpoints.put("randevu sayfası (HTML)", "/" + SLUG);
        endpoints.put("GET /api/settings/public", "/api/settings/public");
        endpoints.put("GET /api/booking/services", "/api/booking/services");
        endpoints.put("GET /api/booking/staff", "/api/booking/staff");
        endpoints.put("GET /api/booking/availability", availabilityPath(0, 0, TARGET_DAY));

        StringBuilder report = new StringBuilder();
        report.append("--- Tek istek başına SQL sayısı ---\n");

        Map<String, Long> measured = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : endpoints.entrySet()) {
            get(client, e.getValue(), null);           // ısınma: lazy init sayılmasın
            rateLimitFilter.reset();
            stats.clear();
            long before = stats.getPrepareStatementCount();
            int status = get(client, e.getValue(), null);
            long sql = stats.getPrepareStatementCount() - before;
            measured.put(e.getKey(), sql);
            report.append(String.format("  %-34s %3d SQL   (HTTP %d)%n", e.getKey(), sql, status));
        }

        report.append(String.format("%n  Gün: %s — %d randevu, %d personel, %d hizmet%n",
                TARGET_DAY, appointmentCountOnTargetDay(), staffIds.size(), serviceIds.size()));
        System.out.print(report);
        writeReport("sql-maliyeti", report.toString());

        assertThat(measured.get("GET /api/booking/availability"))
                .as("müsait saatler ucu SQL sayısı")
                .isNotNull();
    }

    // ─────────────────────────── ölçüm 2: eşzamanlı yük ───────────────────────────

    @Test
    @DisplayName("20 ziyaretçi + 3 personel, 30 saniye")
    void twentyVisitorsAndThreeStaffMembers() throws Exception {
        seedOnce();

        List<String> panelTokens = new ArrayList<>();
        for (String username : PANEL_USERNAMES) {
            panelTokens.add(login(username));
        }

        // Sınır ölçümü kirletmesin: koşu boyunca pencereleri temizle.
        ScheduledExecutorService limitReset = Executors.newSingleThreadScheduledExecutor();
        limitReset.scheduleAtFixedRate(rateLimitFilter::reset, 2, 2, TimeUnit.SECONDS);

        // Havuz doygunluğu ancak koşu sırasında görülebilir; sonradan bakınca boş görünür.
        AtomicInteger peakActive = new AtomicInteger();
        AtomicInteger peakWaiting = new AtomicInteger();
        ScheduledExecutorService poolProbe = Executors.newSingleThreadScheduledExecutor();
        poolProbe.scheduleAtFixedRate(() -> {
            var mx = dataSource.getHikariPoolMXBean();
            peakActive.accumulateAndGet(mx.getActiveConnections(), Math::max);
            peakWaiting.accumulateAndGet(mx.getThreadsAwaitingConnection(), Math::max);
        }, 0, 50, TimeUnit.MILLISECONDS);

        long deadline = System.nanoTime() + RUN_FOR.toNanos();
        ExecutorService pool = Executors.newFixedThreadPool(VISITORS + PANEL_USERS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(VISITORS + PANEL_USERS);

        for (int i = 0; i < VISITORS; i++) {
            final int visitorIndex = i;
            pool.submit(() -> {
                try {
                    start.await();
                    visitorLoop(visitorIndex, deadline);
                } catch (Exception e) {
                    samples.add(new Sample("ziyaretçi/HATA", 0, -1));
                } finally {
                    done.countDown();
                }
            });
        }
        for (int i = 0; i < PANEL_USERS; i++) {
            final String token = panelTokens.get(i);
            pool.submit(() -> {
                try {
                    start.await();
                    panelLoop(token, deadline);
                } catch (Exception e) {
                    samples.add(new Sample("panel/HATA", 0, -1));
                } finally {
                    done.countDown();
                }
            });
        }

        long wallStart = System.currentTimeMillis();
        start.countDown();
        done.await(RUN_FOR.toSeconds() + 60, TimeUnit.SECONDS);
        long wallMillis = System.currentTimeMillis() - wallStart;
        pool.shutdownNow();
        poolProbe.shutdownNow();
        limitReset.shutdownNow();

        String report = buildReport(wallMillis, peakActive.get(), peakWaiting.get());
        System.out.print(report);
        writeReport("yuk", report);

        long failures = samples.stream().filter(s -> s.status() < 200 || s.status() >= 300).count();
        assertThat(failures)
                .as("yük altında başarısız istek olmamalı — rapor: target/yuk-raporu-yuk.txt")
                .isZero();
    }

    // ─────────────────────────── senaryolar ───────────────────────────

    /** booking.js'in gerçekte attığı istek dizisi. */
    private void visitorLoop(int visitorIndex, long deadline) throws Exception {
        HttpClient client = newClient();
        int round = 0;
        while (System.nanoTime() < deadline) {
            timed(client, "ziyaretçi: sayfa", "/" + SLUG, null);
            timed(client, "ziyaretçi: ayarlar", "/api/settings/public", null);
            timed(client, "ziyaretçi: hizmetler", "/api/booking/services", null);
            timed(client, "ziyaretçi: personel", "/api/booking/staff", null);
            // Ziyaretçi birkaç gün deniyor; her deneme ayrı bir müsaitlik isteği.
            for (int day = 0; day < 2 && System.nanoTime() < deadline; day++) {
                timed(client, "ziyaretçi: müsait saatler",
                        availabilityPath(visitorIndex, round, TARGET_DAY.plusDays(day)), null);
                Thread.sleep(THINK_MILLIS);
            }
            round++;
        }
    }

    /** Panelde açık duran takvim ve özet. */
    private void panelLoop(String token, long deadline) throws Exception {
        HttpClient client = newClient();
        while (System.nanoTime() < deadline) {
            timed(client, "panel: takvim", "/api/appointments?date=" + TARGET_DAY, token);
            timed(client, "panel: bugün", "/api/dashboard/today", token);
            Thread.sleep(THINK_MILLIS);
        }
    }

    private String availabilityPath(int visitorIndex, int round, LocalDate date) {
        Long staffId = staffIds.get((visitorIndex + round) % staffIds.size());
        Long serviceId = serviceIds.get((visitorIndex + round) % serviceIds.size());
        return "/api/booking/availability?staffId=" + staffId
                + "&serviceId=" + serviceId + "&date=" + date;
    }

    // ─────────────────────────── HTTP ───────────────────────────

    private HttpClient newClient() {
        // Her sanal ziyaretçi kendi çerez kavanozunu taşır: oturumlar karışmasın.
        return HttpClient.newBuilder()
                .cookieHandler(new CookieManager())
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    private void timed(HttpClient client, String label, String path, String token) throws Exception {
        long t0 = System.nanoTime();
        int status = get(client, path, token);
        samples.add(new Sample(label, (System.nanoTime() - t0) / 1_000_000, status));
    }

    private int get(HttpClient client, String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(30))
                .GET();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        } else {
            // Anonim ziyaretçi salonu adresinden çözüyor; API çağrılarında başlıkla.
            builder.header("X-Salon-Slug", SLUG);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private String login(String username) throws Exception {
        rateLimitFilter.reset();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(
                        Map.of("username", username, "password", PANEL_PASSWORD))))
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("panel girişi: %s", username).isEqualTo(200);
        JsonNode json = objectMapper.readTree(response.body());
        return json.path("data").path("accessToken").asText();
    }

    // ─────────────────────────── rapor ───────────────────────────

    private String buildReport(long wallMillis, int peakActive, int peakWaiting) {
        Map<String, List<Long>> byLabel = new LinkedHashMap<>();
        Map<String, AtomicInteger> errorsByLabel = new LinkedHashMap<>();
        for (Sample s : samples) {
            byLabel.computeIfAbsent(s.label(), k -> new ArrayList<>()).add(s.millis());
            if (s.status() < 200 || s.status() >= 300) {
                errorsByLabel.computeIfAbsent(s.label(), k -> new AtomicInteger()).incrementAndGet();
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n=== YÜK ÖLÇÜMÜ ===\n");
        sb.append(String.format("  %d ziyaretçi + %d personel, %.1f sn, gün %s, %d randevu%n",
                VISITORS, PANEL_USERS, wallMillis / 1000.0, TARGET_DAY, appointmentCountOnTargetDay()));
        sb.append(String.format("  DB havuzu: en çok %d aktif bağlantı, en çok %d bekleyen istek "
                        + "(havuz boyutu %d)%n%n",
                peakActive, peakWaiting, dataSource.getMaximumPoolSize()));
        sb.append(String.format("  %-28s %6s %8s %8s %8s %8s %6s%n",
                "istek", "adet", "p50", "p95", "p99", "en kötü", "hata"));

        int total = 0;
        for (Map.Entry<String, List<Long>> e : byLabel.entrySet()) {
            List<Long> values = new ArrayList<>(e.getValue());
            values.sort(Comparator.naturalOrder());
            total += values.size();
            int errors = errorsByLabel.containsKey(e.getKey())
                    ? errorsByLabel.get(e.getKey()).get() : 0;
            sb.append(String.format("  %-28s %6d %7dms %7dms %7dms %7dms %6d%n",
                    e.getKey(), values.size(), percentile(values, 50), percentile(values, 95),
                    percentile(values, 99), values.get(values.size() - 1), errors));
        }
        sb.append(String.format("%n  Toplam %d istek — %.1f istek/sn%n",
                total, total * 1000.0 / Math.max(1, wallMillis)));
        return sb.toString();
    }

    private static long percentile(List<Long> sorted, int p) {
        if (sorted.isEmpty()) return 0;
        int index = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    private void writeReport(String name, String content) {
        try {
            Path path = Path.of("target", "yuk-raporu-" + name + ".txt");
            Files.createDirectories(path.getParent());
            Files.writeString(path, content);
        } catch (IOException e) {
            System.out.println("Rapor yazılamadı: " + e.getMessage());
        }
    }

    private long appointmentCountOnTargetDay() {
        return txTemplate.execute(status -> (long) appointmentRepository
                .findBySalonIdAndStartTimeBetween(salonId,
                        TARGET_DAY.atStartOfDay(), TARGET_DAY.plusDays(1).atStartOfDay())
                .size());
    }

    // ─────────────────────────── seed ───────────────────────────

    private void seedOnce() {
        if (seeded) {
            return;
        }
        txTemplate.executeWithoutResult(status -> {
            LocalDateTime now = LocalDateTime.now();
            Organization org = organizationRepository.save(Organization.builder()
                    .name("Yük Testi Org").type(OrganizationType.STANDALONE)
                    .active(true).loyaltyPolicy("SALON").createdAt(now).build());
            Salon salon = salonRepository.save(Salon.builder()
                    .organizationId(org.getId()).slug(SLUG).name("Yük Testi Salonu")
                    .timezone("Europe/Istanbul").active(true).createdAt(now).build());
            salonId = salon.getId();

            staffIds = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                Staff staff = staffRepository.save(Staff.builder()
                        .salonId(salonId).name("Uzman " + (i + 1))
                        .role(StaffRole.SPECIALIST).active(true)
                        .colorHex("#e91e8c").createdAt(now).build());
                staffIds.add(staff.getId());
                // Haftanın her günü 09:00-19:00 — üretim penceresiyle aynı.
                for (DayOfWeek day : DayOfWeek.values()) {
                    workingHoursRepository.save(WorkingHours.builder()
                            .salonId(salonId).staffId(staff.getId()).dayOfWeek(day)
                            .startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(19, 0))
                            .dayOff(false).build());
                }
            }

            serviceIds = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                serviceIds.add(serviceRepository.save(ServiceDefinition.builder()
                        .salonId(salonId).name("Hizmet " + (i + 1))
                        .durationMinutes(30 + i * 15)
                        .basePrice(BigDecimal.valueOf(500 + i * 100L))
                        .category(ServiceCategory.OTHER)
                        .active(true).createdAt(now).build()).getId());
            }

            // Dolu bir gün: her uzmanda 10 randevu. Takvim ucu ve slot üretimi
            // boş veriyle ölçülürse hiçbir şey öğrenilmez.
            for (int s = 0; s < staffIds.size(); s++) {
                for (int a = 0; a < 10; a++) {
                    LocalDateTime start = TARGET_DAY.atTime(9, 0).plusMinutes(a * 45L);
                    appointmentRepository.save(Appointment.builder()
                            .salonId(salonId).staffId(staffIds.get(s))
                            .serviceId(serviceIds.get(a % serviceIds.size()))
                            .customerName("Müşteri " + s + "-" + a)
                            .customerPhone("+90555000" + String.format("%04d", s * 10 + a))
                            .startTime(start).endTime(start.plusMinutes(30))
                            .status(AppointmentStatus.SCHEDULED)
                            .basePrice(BigDecimal.valueOf(500))
                            .finalPrice(BigDecimal.valueOf(500))
                            .createdAt(now).build());
                }
            }

            String hash = passwordEncoder.encode(PANEL_PASSWORD);
            UserRole[] panelRoles = {UserRole.RECEPTIONIST, UserRole.BRANCH_MANAGER, UserRole.ADMIN};
            for (int i = 0; i < PANEL_USERS; i++) {
                String username = "yuk-panel-" + i + "-" + SUFFIX;
                userRepository.save(User.builder()
                        .salonId(salonId).organizationId(org.getId())
                        .username(username).passwordHash(hash)
                        .role(panelRoles[i % panelRoles.length])
                        .enabled(true).mustChangePassword(false)
                        .createdAt(now).build());
                PANEL_USERNAMES.add(username);
            }
        });
        seeded = true;
    }
}
