package com.gscrm.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gscrm.model.Organization;
import com.gscrm.model.Salon;
import com.gscrm.model.enums.OrganizationType;
import com.gscrm.model.enums.UserRole;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Arayüzden oluşturulabilen her kayıt türünün gerçekten oluşturulabildiğini sınar.
 *
 * <p>V15 migration'ı tenant sütunlarını {@code NOT NULL} yaptı ama yazma yolları
 * güncellenmedi: bazı servisler yeni kaydın {@code salon_id}'sini hiç doldurmuyor.
 * Bu, yalnızca gerçek bir HTTP isteği kaydı veritabanına yazmaya çalıştığında
 * ortaya çıkar — servis birim testleri sahte repository ile bunu göremez.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Arayüzden kayıt oluşturma")
class UiEntityCreationIT {

    private final String slug = "ui-create-" + java.util.UUID.randomUUID()
            .toString().substring(0, 8);

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TransactionTemplate txTemplate;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private SalonRepository salonRepository;
    @Autowired private StaffRepository staffRepository;
    @Autowired private ServiceDefinitionRepository serviceDefinitionRepository;
    @Autowired private ResourceRepository resourceRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private CouponRepository couponRepository;

    private Long orgId;
    private Long salonId;
    private UsernamePasswordAuthenticationToken manager;

    @BeforeEach
    void seed() {
        txTemplate.executeWithoutResult(status -> {
            salonRepository.findBySlugAndActiveTrue(slug).ifPresent(s -> salonRepository.deleteById(s.getId()));
            LocalDateTime now = LocalDateTime.now();
            Organization org = organizationRepository.save(Organization.builder()
                    .name("Olusturma Org").type(OrganizationType.STANDALONE)
                    .active(true).loyaltyPolicy("SALON").createdAt(now).build());
            orgId = org.getId();
            salonId = salonRepository.save(Salon.builder()
                    .organizationId(orgId).slug(slug).name("Olusturma Salonu")
                    .timezone("Europe/Istanbul").active(true).createdAt(now).build()).getId();
        });

        AuthenticatedUser user = new AuthenticatedUser(
                8200L, "yonetici", "", true, UserRole.BRANCH_MANAGER,
                null, null, salonId, orgId, false, 0,
                List.of(new SimpleGrantedAuthority("ROLE_BRANCH_MANAGER")));
        manager = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    @Test
    @DisplayName("Personel eklenebilir")
    void createStaff() throws Exception {
        assertCreated("/api/staff", Map.of(
                "name", "Yeni Uzman", "role", "SPECIALIST", "colorHex", "#123456", "active", true));
    }

    @Test
    @DisplayName("Hizmet eklenebilir")
    void createService() throws Exception {
        assertCreated("/api/services", Map.of(
                "name", "Fön", "durationMinutes", 30, "basePrice", "150.00",
                "category", "HAIR", "active", true, "requiresResource", false));
    }

    @Test
    @DisplayName("Kaynak eklenebilir")
    void createResource() throws Exception {
        assertCreated("/api/resources", Map.of(
                "name", "Kabin 1", "resourceType", "ROOM", "capacity", 1, "active", true));
    }

    @Test
    @DisplayName("Ürün eklenebilir")
    void createProduct() throws Exception {
        assertCreated("/api/products", Map.of(
                "name", "Şampuan", "price", "199.90", "stockQuantity", 20,
                "lowStockThreshold", 3, "active", true));
    }

    @Test
    @DisplayName("Müşteri eklenebilir")
    void createCustomer() throws Exception {
        assertCreated("/api/customers", Map.of(
                "firstName", "Ayşe", "lastName", "Demir", "phone", "05330001122"));
    }

    @Test
    @DisplayName("Gider eklenebilir")
    void createExpense() throws Exception {
        assertCreated("/api/expenses", Map.of(
                "description", "Elektrik", "amount", "2500.00",
                "expenseDate", java.time.LocalDate.now().toString(), "category", "UTILITIES"));
    }

    @Test
    @DisplayName("Kupon eklenebilir")
    void createCoupon() throws Exception {
        assertCreated("/api/campaigns/coupons", Map.of(
                "code", "TEST10", "description", "Test kuponu",
                "discountType", "PERCENTAGE", "discountValue", "10.00",
                "scope", "SALON"));
    }

    /**
     * İstemcinin gövdeye {@code salonId} koyarak başka bir şubeye kayıt yazmasını
     * engelleyen kontrol. Uçlar ham entity kabul ettiği için bu alan bağlanabilir
     * durumdadır (mass assignment).
     */
    @Test
    @DisplayName("Gövdeye konan salonId başka şubeye kayıt yazamaz")
    void clientSuppliedSalonIdIsIgnored() throws Exception {
        long foreignSalonId = salonId + 9999;
        MvcResult result = perform("/api/products", Map.of(
                "name", "Sızdıran Ürün", "price", "10.00", "stockQuantity", 1,
                "lowStockThreshold", 1, "active", true, "salonId", foreignSalonId));

        boolean wroteToForeignSalon = productRepository.findAll().stream()
                .anyMatch(p -> "Sızdıran Ürün".equals(p.getName())
                        && !salonId.equals(p.getSalonId()));

        assertThat(wroteToForeignSalon)
                .as("İstemcinin gönderdiği salonId kabul edilirse kayıt başka şubeye yazılır "
                        + "(HTTP %s)", result.getResponse().getStatus())
                .isFalse();
    }

    // ─────────────────────────── yardımcılar ───────────────────────────

    private void assertCreated(String uri, Map<String, ?> body) throws Exception {
        MvcResult result = perform(uri, body);
        String responseBody = result.getResponse().getContentAsString();
        assertThat(result.getResponse().getStatus())
                .as("%s → HTTP %s :: %s", uri, result.getResponse().getStatus(), responseBody)
                .isEqualTo(200);
        JsonNode json = objectMapper.readTree(responseBody);
        assertThat(json.path("success").asBoolean())
                .as("%s başarılı dönmeli :: %s", uri, responseBody)
                .isTrue();
    }

    private MvcResult perform(String uri, Map<String, ?> body) throws Exception {
        return mockMvc.perform(post(uri)
                        .with(authentication(manager))
                        .with(csrf())
                        .header("X-Salon-Slug", slug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn();
    }
}
