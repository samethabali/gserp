package com.gscrm.security;

import com.gscrm.model.Appointment;
import com.gscrm.model.Organization;
import com.gscrm.model.Payment;
import com.gscrm.model.Salon;
import com.gscrm.model.ServiceDefinition;
import com.gscrm.model.Staff;
import com.gscrm.model.enums.AppointmentStatus;
import com.gscrm.model.enums.OrganizationType;
import com.gscrm.model.enums.PaymentMethod;
import com.gscrm.model.enums.PaymentStatus;
import com.gscrm.model.enums.ServiceCategory;
import com.gscrm.model.enums.StaffRole;
import com.gscrm.model.enums.UserRole;
import com.gscrm.repository.AppointmentRepository;
import com.gscrm.repository.OrganizationRepository;
import com.gscrm.repository.PaymentRepository;
import com.gscrm.repository.SalonRepository;
import com.gscrm.repository.ServiceDefinitionRepository;
import com.gscrm.repository.StaffRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Uzman (SPECIALIST) rolünün dashboard verisi kendi randevularıyla sınırlıdır.
 *
 * <p>Randevu listesi ve durum değişikliği zaten kişiye kısıtlıyken dashboard
 * salonun tüm cirosunu, tahsilatını ve bütün personelin müşteri adlarını
 * gösteriyordu — bir uzman meslektaşlarının kazancını okuyabiliyordu.
 *
 * <p>Veri commit edilir: test transaction'ı MockMvc isteğinden önce açılsaydı
 * Hibernate tenant filtresi hiç etkinleşmez, test korumayı ölçmeden yeşil verirdi.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Uzman dashboard kapsamı")
class SpecialistDashboardScopeIT {

    private static final BigDecimal OWN_PRICE = new BigDecimal("100.00");
    private static final BigDecimal OTHER_PRICE = new BigDecimal("900.00");
    private static final String OTHER_CUSTOMER = "BaskaninMusterisi";

    @Autowired private MockMvc mockMvc;
    @Autowired private TransactionTemplate txTemplate;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private SalonRepository salonRepository;
    @Autowired private StaffRepository staffRepository;
    @Autowired private ServiceDefinitionRepository serviceDefinitionRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private PaymentRepository paymentRepository;

    private final String slug = "spec-scope-" + java.util.UUID.randomUUID().toString().substring(0, 8);
    private final LocalDate today = LocalDate.now();

    private Long orgId;
    private Long salonId;
    private Long ownStaffId;

    @BeforeEach
    void seed() {
        txTemplate.executeWithoutResult(status -> {
            LocalDateTime now = LocalDateTime.now();
            Organization org = organizationRepository.save(Organization.builder()
                    .name("Uzman Kapsam Org").type(OrganizationType.STANDALONE)
                    .active(true).loyaltyPolicy("SALON").createdAt(now).build());
            orgId = org.getId();
            Salon salon = salonRepository.save(Salon.builder()
                    .organizationId(orgId).slug(slug).name("Uzman Kapsam Salon")
                    .timezone("Europe/Istanbul").active(true).createdAt(now).build());
            salonId = salon.getId();

            Staff own = staffRepository.save(Staff.builder().salonId(salonId).name("Ayse")
                    .role(StaffRole.SPECIALIST).active(true).build());
            Staff other = staffRepository.save(Staff.builder().salonId(salonId).name("Bora")
                    .role(StaffRole.SPECIALIST).active(true).build());
            ownStaffId = own.getId();

            ServiceDefinition service = serviceDefinitionRepository.save(ServiceDefinition.builder()
                    .salonId(salonId).name("Hizmet").durationMinutes(30).basePrice(BigDecimal.TEN)
                    .category(ServiceCategory.HAIR).active(true).requiresResource(false).build());

            seedCompletedAppointment(own.getId(), service.getId(), "KendiMusterim", OWN_PRICE, 10, now);
            seedCompletedAppointment(other.getId(), service.getId(), OTHER_CUSTOMER, OTHER_PRICE, 11, now);
        });
    }

    private void seedCompletedAppointment(Long staffId, Long serviceId, String customerName,
                                          BigDecimal price, int hour, LocalDateTime now) {
        LocalDateTime start = today.atTime(hour, 0);
        Appointment appointment = appointmentRepository.save(Appointment.builder()
                .salonId(salonId).customerName(customerName).customerPhone("0555000" + hour)
                .staffId(staffId).serviceId(serviceId)
                .startTime(start).endTime(start.plusMinutes(30))
                .status(AppointmentStatus.COMPLETED).basePrice(price)
                .adjustment(BigDecimal.ZERO).finalPrice(price)
                .createdAt(now).updatedAt(now).build());

        paymentRepository.save(Payment.builder().salonId(salonId)
                .appointmentId(appointment.getId()).customerName(customerName)
                .customerPhone(appointment.getCustomerPhone())
                .amount(price).method(PaymentMethod.CARD).status(PaymentStatus.PAID)
                .collectedAt(start.plusMinutes(30)).createdAt(now).build());
    }

    @AfterEach
    void cleanUp() {
        txTemplate.executeWithoutResult(status -> {
            paymentRepository.deleteAll(paymentRepository.findAll().stream()
                    .filter(p -> salonId.equals(p.getSalonId())).toList());
            appointmentRepository.deleteAll(appointmentRepository.findAll().stream()
                    .filter(a -> salonId.equals(a.getSalonId())).toList());
            serviceDefinitionRepository.deleteAll(serviceDefinitionRepository.findAll().stream()
                    .filter(s -> salonId.equals(s.getSalonId())).toList());
            staffRepository.deleteAll(staffRepository.findAll().stream()
                    .filter(s -> salonId.equals(s.getSalonId())).toList());
            salonRepository.deleteById(salonId);
            organizationRepository.deleteById(orgId);
        });
    }

    @Test
    @DisplayName("günlük özet yalnızca uzmanın kendi randevularını sayar")
    void dailySummaryIsScopedToOwnAppointments() throws Exception {
        String body = dashboard(specialist());

        assertThat(body)
                .as("uzman başka personelin müşterisini görmemeli")
                .doesNotContain(OTHER_CUSTOMER);
        assertThat(body)
                .as("uzman yalnızca kendi randevusunu ve cirosunu görmeli")
                .contains("\"totalAppointments\":1")
                .contains("\"totalRevenue\":100.00")
                .contains("\"collectedRevenue\":100.00");
        assertThat(body)
                .as("performans kartlarında yalnızca uzmanın kendisi olmalı")
                .contains("Ayse")
                .doesNotContain("Bora");
    }

    @Test
    @DisplayName("yönetici günlük özette salonun tamamını görmeye devam eder")
    void managerStillSeesWholeSalon() throws Exception {
        String body = dashboard(manager());

        assertThat(body)
                .as("yönetici için kapsam daralmamalı")
                .contains("\"totalAppointments\":2")
                .contains("\"totalRevenue\":1000.00")
                .contains(OTHER_CUSTOMER)
                .contains("Bora");
    }

    @Test
    @DisplayName("gelir trendi de uzmanın kendi randevularıyla sınırlı")
    void trendIsScopedToOwnAppointments() throws Exception {
        assertThat(trend(specialist()))
                .as("uzmanın trendi kendi cirosunu göstermeli")
                .contains("\"revenue\":100.00");
        assertThat(trend(manager()))
                .as("yöneticinin trendi salon cirosunu göstermeli")
                .contains("\"revenue\":1000.00");
    }

    private String dashboard(UsernamePasswordAuthenticationToken auth) throws Exception {
        return mockMvc.perform(get("/api/dashboard")
                        .param("date", today.toString())
                        .with(authentication(auth))
                        .header("X-Salon-Slug", slug))
                .andReturn().getResponse().getContentAsString();
    }

    private String trend(UsernamePasswordAuthenticationToken auth) throws Exception {
        return mockMvc.perform(get("/api/dashboard/trend")
                        .param("days", "1")
                        .with(authentication(auth))
                        .header("X-Salon-Slug", slug))
                .andReturn().getResponse().getContentAsString();
    }

    private UsernamePasswordAuthenticationToken specialist() {
        return authFor(UserRole.SPECIALIST, ownStaffId);
    }

    private UsernamePasswordAuthenticationToken manager() {
        return authFor(UserRole.BRANCH_MANAGER, null);
    }

    private UsernamePasswordAuthenticationToken authFor(UserRole role, Long staffId) {
        AuthenticatedUser user = new AuthenticatedUser(
                8101L, "scope-" + role.name().toLowerCase(), "", true, role,
                staffId, null, salonId, orgId, false, 0,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }
}
