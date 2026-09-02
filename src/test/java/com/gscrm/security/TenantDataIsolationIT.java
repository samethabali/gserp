package com.gscrm.security;

import com.gscrm.model.*;
import com.gscrm.model.enums.*;
import com.gscrm.repository.*;
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
 * Tenant veri izolasyonunun modül modül doğrulaması.
 *
 * <p><b>Neden sınıf düzeyinde {@code @Transactional} yok:</b> test transaction'ı,
 * MockMvc isteği başlamadan — yani {@code TenantFilter} bağlamı kurmadan — açılır.
 * O durumda Hibernate tenant filtresi hiç etkinleşmez ve test, korumayı ölçmeden
 * yeşil verir. Bu yüzden veri commit edilir, istek kendi transaction'ını açar ve
 * temizlik {@code @AfterEach} içinde elle yapılır.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Tenant veri izolasyonu")
class TenantDataIsolationIT {

    private static final String SLUG_A = "iso-a";
    private static final String SLUG_B = "iso-b";

    private static final String B_CUSTOMER = "BGizliMusteri";
    private static final String B_PHONE = "05559990001";
    private static final String B_PRODUCT = "BGizliUrun";
    private static final String B_RESOURCE = "BGizliKabin";
    private static final String B_EXPENSE = "BGizliGider";
    private static final BigDecimal B_AMOUNT = new BigDecimal("7777.00");

    @Autowired private MockMvc mockMvc;
    @Autowired private TransactionTemplate txTemplate;

    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private SalonRepository salonRepository;
    @Autowired private StaffRepository staffRepository;
    @Autowired private ServiceDefinitionRepository serviceDefinitionRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ResourceRepository resourceRepository;
    @Autowired private CustomerRepository customerRepository;

    private Long orgAId;
    private Long orgBId;
    private Long salonAId;
    private Long salonBId;

    private UsernamePasswordAuthenticationToken salonAManager;

    @BeforeEach
    void seedTwoTenants() {
        txTemplate.executeWithoutResult(status -> {
            LocalDateTime now = LocalDateTime.now();

            Organization orgA = organizationRepository.save(org("Izolasyon A", now));
            Organization orgB = organizationRepository.save(org("Izolasyon B", now));
            orgAId = orgA.getId();
            orgBId = orgB.getId();

            Salon a = salonRepository.save(salon(orgA.getId(), SLUG_A, "A Salonu", now));
            Salon b = salonRepository.save(salon(orgB.getId(), SLUG_B, "B Salonu", now));
            salonAId = a.getId();
            salonBId = b.getId();

            // A salonuna zararsız birer kayıt — filtrenin "her şeyi" boşaltmadığını görmek için.
            seedSalonData(salonAId, "AKendiMusteri", "05551110001", "AKendiUrun",
                    "AKendiKabin", "AKendiGider", new BigDecimal("100.00"), now);

            // B salonuna, A'nın asla görmemesi gereken kayıtlar.
            seedSalonData(salonBId, B_CUSTOMER, B_PHONE, B_PRODUCT,
                    B_RESOURCE, B_EXPENSE, B_AMOUNT, now);
        });

        AuthenticatedUser manager = new AuthenticatedUser(
                9001L, "yonetici@a", "", true, UserRole.BRANCH_MANAGER,
                null, null, salonAId, orgAId, false, 0,
                List.of(new SimpleGrantedAuthority("ROLE_BRANCH_MANAGER")));
        salonAManager = new UsernamePasswordAuthenticationToken(manager, null, manager.getAuthorities());
    }

    @AfterEach
    void cleanUp() {
        txTemplate.executeWithoutResult(status -> {
            for (Long salonId : List.of(salonAId, salonBId)) {
                paymentRepository.deleteAll(paymentRepository.findAll().stream()
                        .filter(p -> salonId.equals(p.getSalonId())).toList());
                appointmentRepository.deleteAll(appointmentRepository.findAll().stream()
                        .filter(x -> salonId.equals(x.getSalonId())).toList());
                expenseRepository.deleteAll(expenseRepository.findAll().stream()
                        .filter(x -> salonId.equals(x.getSalonId())).toList());
                productRepository.deleteAll(productRepository.findAll().stream()
                        .filter(x -> salonId.equals(x.getSalonId())).toList());
                resourceRepository.deleteAll(resourceRepository.findAll().stream()
                        .filter(x -> salonId.equals(x.getSalonId())).toList());
                customerRepository.deleteAll(customerRepository.findAll().stream()
                        .filter(x -> salonId.equals(x.getSalonId())).toList());
                serviceDefinitionRepository.deleteAll(serviceDefinitionRepository.findAll().stream()
                        .filter(x -> salonId.equals(x.getSalonId())).toList());
                staffRepository.deleteAll(staffRepository.findAll().stream()
                        .filter(x -> salonId.equals(x.getSalonId())).toList());
            }
            salonRepository.deleteById(salonAId);
            salonRepository.deleteById(salonBId);
            organizationRepository.deleteById(orgAId);
            organizationRepository.deleteById(orgBId);
        });
    }

    // ─────────────────────────── testler ───────────────────────────

    @Test
    @DisplayName("Ödeme listesi başka salonun tahsilatını sızdırmaz")
    void paymentsListIsScoped() throws Exception {
        String body = callAsSalonA("/api/payments?date=" + LocalDate.now());
        assertThat(body).doesNotContain(B_CUSTOMER);
        assertThat(body).doesNotContain(B_PHONE);
        assertThat(body).contains("AKendiMusteri");
    }

    @Test
    @DisplayName("Günlük ciro özeti başka salonun cirosunu toplamaz")
    void paymentSummaryIsScoped() throws Exception {
        String body = callAsSalonA("/api/payments/summary?date=" + LocalDate.now());
        assertThat(body).doesNotContain("7777");
        assertThat(body).contains("100.00");
    }

    @Test
    @DisplayName("Telefonla ödeme sorgusu başka salonun geçmişini vermez")
    void paymentByPhoneIsScoped() throws Exception {
        String body = callAsSalonA("/api/payments/customer?phone=" + B_PHONE);
        assertThat(body).doesNotContain("7777");
        assertThat(body).doesNotContain(B_CUSTOMER);
    }

    @Test
    @DisplayName("Gider listesi başka salonun giderlerini sızdırmaz")
    void expensesAreScoped() throws Exception {
        String body = callAsSalonA("/api/expenses?from=" + LocalDate.now().minusDays(1)
                + "&to=" + LocalDate.now().plusDays(1));
        assertThat(body).doesNotContain(B_EXPENSE);
        assertThat(body).contains("AKendiGider");
    }

    @Test
    @DisplayName("Ürün listesi başka salonun stoğunu sızdırmaz")
    void productsAreScoped() throws Exception {
        String body = callAsSalonA("/api/products");
        assertThat(body).doesNotContain(B_PRODUCT);
        assertThat(body).contains("AKendiUrun");
    }

    @Test
    @DisplayName("Kaynak listesi başka salonun kabinlerini sızdırmaz")
    void resourcesAreScoped() throws Exception {
        String body = callAsSalonA("/api/resources");
        assertThat(body).doesNotContain(B_RESOURCE);
        assertThat(body).contains("AKendiKabin");
    }

    @Test
    @DisplayName("Müşteri listesi başka salonun müşterilerini sızdırmaz")
    void customersAreScoped() throws Exception {
        String body = callAsSalonA("/api/customers");
        assertThat(body).doesNotContain(B_CUSTOMER);
        assertThat(body).doesNotContain(B_PHONE);
    }

    @Test
    @DisplayName("Randevu listesi başka salonun randevularını sızdırmaz")
    void appointmentsAreScoped() throws Exception {
        String body = callAsSalonA("/api/appointments?date=" + LocalDate.now().plusDays(3));
        assertThat(body).doesNotContain(B_CUSTOMER);
    }

    // ─────────────────────────── yardımcılar ───────────────────────────

    private String callAsSalonA(String uri) throws Exception {
        var response = mockMvc.perform(get(uri)
                        .with(authentication(salonAManager))
                        .header("X-Salon-Slug", SLUG_A))
                .andReturn().getResponse();
        // 403, filtrenin kaçırdığı bir kaydı TenantEntityListener'ın yakaladığı anlamına gelir.
        // Sızıntı yok demektir ama filtre kapsamı eksiktir — bunu başarı saymıyoruz.
        assertThat(response.getStatus())
                .as("%s tenant filtresi tarafından kapsanmalı (403 = filtre boşluğu)", uri)
                .isEqualTo(200);
        return response.getContentAsString();
    }

    private Organization org(String name, LocalDateTime now) {
        return Organization.builder().name(name).type(OrganizationType.STANDALONE)
                .active(true).loyaltyPolicy("SALON").createdAt(now).build();
    }

    private Salon salon(Long orgId, String slug, String name, LocalDateTime now) {
        return Salon.builder().organizationId(orgId).slug(slug).name(name)
                .timezone("Europe/Istanbul").active(true).createdAt(now).build();
    }

    private void seedSalonData(Long salonId, String customerName, String phone, String productName,
                               String resourceName, String expenseName, BigDecimal amount,
                               LocalDateTime now) {
        Staff staff = staffRepository.save(Staff.builder().salonId(salonId).name("Uzman")
                .role(StaffRole.SPECIALIST).active(true).build());
        ServiceDefinition service = serviceDefinitionRepository.save(ServiceDefinition.builder()
                .salonId(salonId).name("Hizmet").durationMinutes(30).basePrice(BigDecimal.TEN)
                .category(ServiceCategory.HAIR).active(true).requiresResource(false).build());

        LocalDateTime start = LocalDate.now().plusDays(3).atTime(14, 0);
        Appointment appointment = appointmentRepository.save(Appointment.builder()
                .salonId(salonId).customerName(customerName).customerPhone(phone)
                .staffId(staff.getId()).serviceId(service.getId())
                .startTime(start).endTime(start.plusMinutes(30))
                .status(AppointmentStatus.SCHEDULED).basePrice(BigDecimal.TEN)
                .adjustment(BigDecimal.ZERO).finalPrice(BigDecimal.TEN)
                .createdAt(now).updatedAt(now).build());

        customerRepository.save(Customer.builder().salonId(salonId).homeSalonId(salonId)
                .firstName(customerName).lastName("Test").phone(phone).createdAt(now).build());

        paymentRepository.save(Payment.builder().salonId(salonId)
                .appointmentId(appointment.getId()).customerName(customerName).customerPhone(phone)
                .amount(amount).method(PaymentMethod.CARD).status(PaymentStatus.PAID)
                .collectedAt(LocalDate.now().atTime(12, 0)).createdAt(now).build());

        expenseRepository.save(Expense.builder().salonId(salonId).description(expenseName)
                .amount(BigDecimal.ONE).category(ExpenseCategory.OTHER)
                .expenseDate(LocalDate.now()).createdAt(now).build());

        productRepository.save(Product.builder().salonId(salonId).name(productName)
                .price(BigDecimal.ONE).stockQuantity(5).active(true).createdAt(now).build());

        resourceRepository.save(Resource.builder().salonId(salonId).name(resourceName)
                .resourceType(ResourceType.ROOM).capacity(1).active(true).createdAt(now).build());
    }
}
