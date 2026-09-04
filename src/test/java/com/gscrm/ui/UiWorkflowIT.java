package com.gscrm.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gscrm.model.*;
import com.gscrm.model.enums.*;
import com.gscrm.repository.*;
import com.gscrm.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import com.gscrm.repository.OrganizationSubscriptionRepository;
import com.gscrm.repository.SubscriptionPlanRepository;
import com.gscrm.support.SubscriptionFixtures;

/**
 * Bir resepsiyonistin günlük akışını uçtan uca yürütür.
 *
 * <p>Amaç tek tek uçları değil, <em>zinciri</em> sınamak: bir adımın çıktısı bir
 * sonrakinin girdisidir. Tenant filtresi gibi kesişen değişiklikler tam olarak bu
 * zincirlerde kırılır — tekil testler yeşil kalırken kullanıcı akışı çalışmaz.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Arayüz kullanım akışı")
class UiWorkflowIT {

    private final String slug = "ui-flow-" + java.util.UUID.randomUUID()
            .toString().substring(0, 8);

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TransactionTemplate txTemplate;

    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private SalonRepository salonRepository;
    @Autowired private StaffRepository staffRepository;
    @Autowired private ServiceDefinitionRepository serviceDefinitionRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private CustomerRepository customerRepository;

    @Autowired private OrganizationSubscriptionRepository subscriptionRepository;

    @Autowired private SubscriptionPlanRepository subscriptionPlanRepository;


    private Long orgId;
    private Long salonId;
    private Long staffId;
    private Long serviceId;
    private UsernamePasswordAuthenticationToken receptionist;

    @BeforeEach
    void seed() {
        txTemplate.executeWithoutResult(status -> {
            LocalDateTime now = LocalDateTime.now();
            Organization org = organizationRepository.save(Organization.builder()
                    .name("Akis Org").type(OrganizationType.STANDALONE)
                    .active(true).loyaltyPolicy("SALON").createdAt(now).build());
            orgId = org.getId();
            Salon salon = salonRepository.save(Salon.builder()
                    .organizationId(orgId).slug(slug).name("Akis Salonu")
                    .timezone("Europe/Istanbul").active(true).createdAt(now).build());
            salonId = salon.getId();
            SubscriptionFixtures.seedTrial(subscriptionRepository, subscriptionPlanRepository, orgId);
            staffId = staffRepository.save(Staff.builder().salonId(salonId).name("Zeynep")
                    .role(StaffRole.SPECIALIST).colorHex("#aa00aa").active(true).build()).getId();
            serviceId = serviceDefinitionRepository.save(ServiceDefinition.builder()
                    .salonId(salonId).name("Saç Kesim").durationMinutes(30)
                    .basePrice(new BigDecimal("400.00")).category(ServiceCategory.HAIR)
                    .active(true).requiresResource(false).build()).getId();
        });

        AuthenticatedUser user = new AuthenticatedUser(
                8100L, "resepsiyon", "", true, UserRole.RECEPTIONIST,
                null, null, salonId, orgId, false, 0,
                List.of(new SimpleGrantedAuthority("ROLE_RECEPTIONIST")));
        receptionist = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    @Test
    @DisplayName("Müşteri kaydı → randevu → tahsilat → panoda görünür")
    void receptionDeskHappyPath() throws Exception {
        // 1. Müşteri kaydı
        JsonNode customer = callJson(post("/api/customers").content(json(Map.of(
                "firstName", "Elif", "lastName", "Kaya", "phone", "05321234567"))));
        assertThat(customer.path("success").asBoolean())
                .as("Müşteri oluşturulmalı: %s", customer).isTrue();

        // 2. Randevu
        LocalDateTime start = LocalDate.now().plusDays(1).atTime(11, 0);
        JsonNode appointment = callJson(post("/api/appointments").content(json(Map.of(
                "customerName", "Elif Kaya",
                "customerPhone", "05321234567",
                "staffId", staffId,
                "serviceId", serviceId,
                "startTime", start.toString()))));
        assertThat(appointment.path("success").asBoolean())
                .as("Randevu oluşturulmalı: %s", appointment).isTrue();
        long appointmentId = appointment.path("data").path("id").asLong();
        assertThat(appointmentId).as("Randevu id dönmeli").isPositive();

        // 3. Tahsilat
        JsonNode payment = callJson(post("/api/payments").content(json(Map.of(
                "appointmentId", appointmentId,
                "amount", "400.00",
                "method", "CARD"))));
        assertThat(payment.path("success").asBoolean())
                .as("Tahsilat kaydedilmeli: %s", payment).isTrue();

        // 4. Günlük özet tahsilatı yansıtmalı
        JsonNode summary = callJson(get("/api/payments/summary?date=" + LocalDate.now()));
        assertThat(summary.path("data").path("cardTotal").asDouble())
                .as("Kart toplamı tahsilatı içermeli").isEqualTo(400.0);

        // 5. Randevu listesi randevuyu göstermeli
        JsonNode list = callJson(get("/api/appointments?date=" + LocalDate.now().plusDays(1)));
        assertThat(list.toString())
                .as("Randevu listesi müşteriyi göstermeli").contains("Elif Kaya");
    }

    @Test
    @DisplayName("Ürün ekle → sat → stok düşer")
    void productSaleFlow() throws Exception {
        JsonNode created = callJson(post("/api/products").content(json(Map.of(
                "name", "Saç Serumu", "price", "250.00", "stockQuantity", 10,
                "lowStockThreshold", 2, "active", true))));
        assertThat(created.path("success").asBoolean())
                .as("Ürün oluşturulmalı: %s", created).isTrue();
        long productId = created.path("data").path("id").asLong();

        JsonNode sold = callJson(post("/api/products/" + productId + "/sell").content(json(Map.of(
                "quantity", 3))));
        assertThat(sold.path("success").asBoolean())
                .as("Ürün satışı kaydedilmeli: %s", sold).isTrue();

        JsonNode products = callJson(get("/api/products"));
        assertThat(products.toString()).contains("Saç Serumu");
        int remaining = products.path("data").get(0).path("stockQuantity").asInt();
        assertThat(remaining).as("Satış sonrası stok 10-3=7 olmalı").isEqualTo(7);
    }

    @Test
    @DisplayName("Gider ekle → listede ve özette görünür")
    void expenseFlow() throws Exception {
        JsonNode created = callJson(post("/api/expenses").content(json(Map.of(
                "description", "Kira", "amount", "15000.00",
                "expenseDate", LocalDate.now().toString(), "category", "RENT"))));
        assertThat(created.path("success").asBoolean())
                .as("Gider oluşturulmalı: %s", created).isTrue();

        JsonNode list = callJson(get("/api/expenses?from=" + LocalDate.now()
                + "&to=" + LocalDate.now()));
        assertThat(list.toString()).as("Gider listede görünmeli").contains("Kira");
    }

    @Test
    @DisplayName("Herkese açık randevu formu betik enjeksiyonunu reddeder")
    void publicBookingRejectsScriptInjection() throws Exception {
        LocalDateTime start = LocalDate.now().plusDays(2).atTime(15, 0);
        MvcResult result = mockMvc.perform(post("/api/booking/request")
                        .header("X-Salon-Slug", slug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "customerName", "<img src=x onerror=alert(1)>",
                                "customerPhone", "05321234567",
                                "staffId", staffId,
                                "serviceId", serviceId,
                                "startTime", start.toString()))))
                .andReturn();

        assertThat(result.getResponse().getStatus())
                .as("Betik içeren müşteri adı doğrulamada reddedilmeli")
                .isEqualTo(400);
        assertThat(appointmentRepository.findAll().stream()
                .anyMatch(a -> a.getCustomerName() != null && a.getCustomerName().contains("<img")))
                .as("Betik yükü veritabanına yazılmamalı")
                .isFalse();
    }

    // ─────────────────────────── yardımcılar ───────────────────────────

    private String json(Map<String, ?> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private JsonNode callJson(MockHttpServletRequestBuilder builder) throws Exception {
        MvcResult result = mockMvc.perform(builder
                        .with(authentication(receptionist))
                        .with(csrf())
                        .header("X-Salon-Slug", slug)
                        .contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body)
                .as("%s boş gövde döndü (HTTP %s)",
                        result.getRequest().getRequestURI(), result.getResponse().getStatus())
                .isNotBlank();
        return objectMapper.readTree(body);
    }
}
