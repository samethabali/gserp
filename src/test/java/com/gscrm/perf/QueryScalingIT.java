package com.gscrm.perf;

import com.gscrm.model.Appointment;
import com.gscrm.model.Organization;
import com.gscrm.model.Salon;
import com.gscrm.model.ServiceDefinition;
import com.gscrm.model.Staff;
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
import com.gscrm.repository.WorkingHoursRepository;
import com.gscrm.security.AuthenticatedUser;
import com.gscrm.security.RateLimitFilter;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sıcak uçların SQL maliyeti veriyle birlikte büyümemeli.
 *
 * <p>Yük ölçümünde ({@code BookingLoadIT}) iki darboğaz çıktı ve ikisi de aynı
 * sınıftandı — istek başına sorgu sayısı veri miktarıyla doğru orantılı artıyordu:
 *
 * <ul>
 *   <li>Müsait saatler ucu her <b>slot</b> için ayrı bir çakışma sorgusu atıyordu.</li>
 *   <li>{@code Appointment} üç EAGER koleksiyon taşıyor ve Hibernate bunları tek
 *       sorguda birleştiremediği için her <b>randevu</b> üç ek SELECT üretiyordu.</li>
 * </ul>
 *
 * <p>Tek istek 79 SQL'e çıkıyor, yirmi eşzamanlı ziyaretçide on bağlantılık havuz
 * doyuyordu.
 *
 * <p>Testler sabit bir eşik değil <b>değişmez</b> ölçüyor: aynı ucu iki farklı veri
 * büyüklüğüyle çağırıp sorgu sayılarının eşit olmasını bekliyor. Sabit sayı
 * yazsaydık ilgisiz bir yerde eklenen bir sorgu testi kırardı; böyleyse yalnızca
 * gerçek regresyon — "başına bir sorgu" düzenine dönüş — yakalanır.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@DisplayName("Sorgu maliyeti veriyle büyümemeli")
class QueryScalingIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private TransactionTemplate txTemplate;
    @Autowired private RateLimitFilter rateLimitFilter;
    @Autowired private EntityManagerFactory entityManagerFactory;

    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private SalonRepository salonRepository;
    @Autowired private StaffRepository staffRepository;
    @Autowired private ServiceDefinitionRepository serviceRepository;
    @Autowired private WorkingHoursRepository workingHoursRepository;
    @Autowired private AppointmentRepository appointmentRepository;

    private static final String SLUG = "olcek-" + UUID.randomUUID().toString().substring(0, 8);
    /** Yarın: geçmiş saatler elenmesin, mesai penceresinin tamamı slot üretsin. */
    private static final LocalDate BUSY_DAY = LocalDate.now().plusDays(1);
    private static final LocalDate QUIET_DAY = LocalDate.now().plusDays(2);

    private static Long orgId;
    private static Long salonId;
    private static Long shortShiftStaffId;
    private static Long longShiftStaffId;
    private static Long serviceId;

    @BeforeEach
    void seed() {
        rateLimitFilter.reset();
        if (salonId != null) {
            return;
        }
        txTemplate.executeWithoutResult(status -> {
            LocalDateTime now = LocalDateTime.now();
            Organization org = organizationRepository.save(Organization.builder()
                    .name("Ölçek Org").type(OrganizationType.STANDALONE)
                    .active(true).loyaltyPolicy("SALON").createdAt(now).build());
            orgId = org.getId();
            salonId = salonRepository.save(Salon.builder()
                    .organizationId(orgId).slug(SLUG).name("Ölçek Salonu")
                    .timezone("Europe/Istanbul").active(true).createdAt(now).build()).getId();

            serviceId = serviceRepository.save(ServiceDefinition.builder()
                    .salonId(salonId).name("Kesim").durationMinutes(30)
                    .basePrice(BigDecimal.valueOf(500)).category(ServiceCategory.OTHER)
                    .active(true).createdAt(now).build()).getId();

            // İki uzman yalnızca mesai uzunluğuyla ayrışıyor: 2 saat (~5 slot)
            // ve 10 saat (~39 slot). Randevu sayıları eşit tutuluyor ki müsaitlik
            // ölçümündeki fark yalnızca slot sayısından gelsin.
            shortShiftStaffId = staffWithShift(now, "Kısa Mesai", LocalTime.of(9, 0), LocalTime.of(11, 0));
            longShiftStaffId = staffWithShift(now, "Uzun Mesai", LocalTime.of(9, 0), LocalTime.of(19, 0));
            for (Long staffId : List.of(shortShiftStaffId, longShiftStaffId)) {
                appointmentsOn(now, BUSY_DAY, staffId, 6);
            }

            // Takvim ölçümü randevu sayısını değiştirmek istiyor; bunu üçüncü bir
            // uzmanda yapıyoruz ki müsaitlik ölçümünün kurgusu bozulmasın.
            Long calendarStaffId = staffWithShift(now, "Takvim", LocalTime.of(9, 0), LocalTime.of(19, 0));
            appointmentsOn(now, BUSY_DAY, calendarStaffId, 28);
            appointmentsOn(now, QUIET_DAY, calendarStaffId, 3);
        });
    }

    private Long staffWithShift(LocalDateTime now, String name, LocalTime open, LocalTime close) {
        Staff staff = staffRepository.save(Staff.builder()
                .salonId(salonId).name(name).role(StaffRole.SPECIALIST)
                .active(true).colorHex("#e91e8c").createdAt(now).build());
        for (DayOfWeek dow : DayOfWeek.values()) {
            workingHoursRepository.save(WorkingHours.builder()
                    .salonId(salonId).staffId(staff.getId()).dayOfWeek(dow)
                    .startTime(open).endTime(close).dayOff(false).build());
        }
        return staff.getId();
    }

    /**
     * Ardışık, çakışmayan randevular.
     *
     * <p>Veritabanında aynı uzman için zaman aralığı çakışmasını yasaklayan bir
     * dışlama kısıtı var ({@code excl_appointment_staff_overlap}); süre ve adım
     * eşit tutulmazsa seed'in kendisi düşer.
     */
    private void appointmentsOn(LocalDateTime now, LocalDate day, Long staffId, int count) {
        int slotMinutes = 15;
        for (int i = 0; i < count; i++) {
            LocalDateTime start = day.atTime(9, 0).plusMinutes((long) i * slotMinutes);
            appointmentRepository.save(Appointment.builder()
                    .salonId(salonId).staffId(staffId).serviceId(serviceId)
                    .customerName("Müşteri " + staffId + "-" + i)
                    .customerPhone("+90555" + String.format("%07d", (staffId * 1000 + i) % 10000000))
                    .startTime(start).endTime(start.plusMinutes(slotMinutes))
                    .status(AppointmentStatus.SCHEDULED)
                    .basePrice(BigDecimal.valueOf(500)).finalPrice(BigDecimal.valueOf(500))
                    .createdAt(now).build());
        }
    }

    @Test
    @DisplayName("müsait saatler — sorgu sayısı slot sayısından bağımsız")
    void availabilityDoesNotQueryPerSlot() throws Exception {
        long shortShift = measure(availabilityRequest(shortShiftStaffId));
        long longShift = measure(availabilityRequest(longShiftStaffId));

        assertThat(longShift)
                .as("10 saatlik mesai %d SQL, 2 saatlik mesai %d SQL — slot başına sorgu geri gelmiş",
                        longShift, shortShift)
                .isEqualTo(shortShift);
        assertThat(longShift).as("uç hiç sorgu atmıyorsa ölçüm yanlış kurulmuş").isPositive();
    }

    @Test
    @DisplayName("takvim — sorgu sayısı randevu sayısından bağımsız")
    void calendarDoesNotQueryPerAppointment() throws Exception {
        long quietDay = measure(calendarRequest(QUIET_DAY));
        long busyDay = measure(calendarRequest(BUSY_DAY));

        assertThat(busyDay)
                .as("40 randevulu gün %d SQL, 3 randevulu gün %d SQL — randevu başına sorgu var",
                        busyDay, quietDay)
                .isEqualTo(quietDay);
        assertThat(busyDay).as("uç hiç sorgu atmıyorsa ölçüm yanlış kurulmuş").isPositive();
    }

    // ─────────────────────────── ölçüm ───────────────────────────

    /** Isınma + ölçüm: tembel başlatmalar ve şablon derlemesi sayıma karışmasın. */
    private long measure(MockHttpServletRequestBuilder request) throws Exception {
        perform(request);
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        long before = stats.getPrepareStatementCount();
        perform(request);
        return stats.getPrepareStatementCount() - before;
    }

    private void perform(MockHttpServletRequestBuilder request) throws Exception {
        rateLimitFilter.reset();
        mockMvc.perform(request).andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder availabilityRequest(Long staffId) {
        return get("/api/booking/availability")
                .param("staffId", String.valueOf(staffId))
                .param("serviceId", String.valueOf(serviceId))
                .param("date", BUSY_DAY.toString())
                .header("X-Salon-Slug", SLUG);
    }

    private MockHttpServletRequestBuilder calendarRequest(LocalDate date) {
        AuthenticatedUser manager = new AuthenticatedUser(
                9401L, "olcek-yonetici", "", true, UserRole.BRANCH_MANAGER,
                null, null, salonId, orgId, false, 0,
                List.of(new SimpleGrantedAuthority("ROLE_BRANCH_MANAGER")));
        return get("/api/appointments")
                .param("date", date.toString())
                .with(authentication(UsernamePasswordAuthenticationToken.authenticated(
                        manager, null, manager.getAuthorities())))
                .header("X-Salon-Slug", SLUG);
    }
}
