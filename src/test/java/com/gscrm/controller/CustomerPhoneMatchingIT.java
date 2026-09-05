package com.gscrm.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gscrm.model.Organization;
import com.gscrm.model.Salon;
import com.gscrm.model.enums.OrganizationType;
import com.gscrm.model.enums.UserRole;
import com.gscrm.repository.CustomerRepository;
import com.gscrm.repository.OrganizationRepository;
import com.gscrm.repository.SalonRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import com.gscrm.repository.OrganizationSubscriptionRepository;
import com.gscrm.repository.SubscriptionPlanRepository;
import com.gscrm.support.SubscriptionFixtures;

/**
 * Panel tarafında telefon normalizasyonunun davranışını gerçek HTTP ile sınar.
 *
 * <p>Buradaki üç şey birim testinden görünmez: eşsiz index'i (V30'da düşürülen
 * {@code uk_customer_salon_phone}) gerçekten kaldırıldığı için aynı normalize
 * telefonlu ikinci kaydın <b>yazılabildiği</b>, doğrulama kısıtının uçtan uca
 * uygulandığı ve yinelenen ucunun gerçek veriyle çalıştığı.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Panelde telefon eşleştirme")
class CustomerPhoneMatchingIT {

    private final String slug = "phone-match-" + java.util.UUID.randomUUID().toString().substring(0, 8);

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TransactionTemplate txTemplate;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private SalonRepository salonRepository;
    @Autowired private CustomerRepository customerRepository;

    @Autowired private OrganizationSubscriptionRepository subscriptionRepository;

    @Autowired private SubscriptionPlanRepository subscriptionPlanRepository;


    private Long salonId;
    private UsernamePasswordAuthenticationToken manager;

    @BeforeEach
    void seed() {
        txTemplate.executeWithoutResult(status -> {
            LocalDateTime now = LocalDateTime.now();
            Organization org = organizationRepository.save(Organization.builder()
                    .name("Telefon Org").type(OrganizationType.STANDALONE)
                    .active(true).loyaltyPolicy("SALON").createdAt(now).build());
            salonId = salonRepository.save(Salon.builder()
                    .organizationId(org.getId()).slug(slug).name("Telefon Salonu")
                    .timezone("Europe/Istanbul").active(true).createdAt(now).build()).getId();
            SubscriptionFixtures.seedTrial(subscriptionRepository, subscriptionPlanRepository, org.getId());

            AuthenticatedUser user = new AuthenticatedUser(
                    8300L, "yonetici", "", true, UserRole.BRANCH_MANAGER,
                    null, null, salonId, org.getId(), false, 0,
                    List.of(new SimpleGrantedAuthority("ROLE_BRANCH_MANAGER")));
            manager = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        });
    }

    private MvcResult postCustomer(Map<String, Object> body) throws Exception {
        return mockMvc.perform(post("/api/customers")
                        .with(authentication(manager)).with(csrf())
                        .header("X-Salon-Slug", slug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn();
    }

    @Test
    @DisplayName("Farklı yazımlar aynı kanonik telefona düşer")
    void differentSpellingsProduceTheSameCanonicalValue() throws Exception {
        postCustomer(Map.of("firstName", "Ayşe", "lastName", "Yılmaz", "phone", "0532 111 22 33"));

        txTemplate.executeWithoutResult(status ->
                assertThat(customerRepository.findBySalonIdAndPhoneNormalized(salonId, "+905321112233"))
                        .hasSize(1)
                        .allSatisfy(c -> {
                            // Ham hâli korunur: salon müşterinin yazdığını görmeye devam eder.
                            assertThat(c.getPhone()).isEqualTo("0532 111 22 33");
                            assertThat(c.getPhoneNormalized()).isEqualTo("+905321112233");
                        }));
    }

    @Test
    @DisplayName("Aynı telefonla ikinci kayıt 409 ile uyarır, allowDuplicate ile geçilir")
    void duplicatePhoneIsGuardedButCanBeOverridden() throws Exception {
        postCustomer(Map.of("firstName", "Ayşe", "lastName", "Yılmaz", "phone", "05321112233"));

        MvcResult blocked = postCustomer(Map.of("firstName", "Ayse", "phone", "+90 532 111 22 33"));
        assertThat(blocked.getResponse().getStatus()).isEqualTo(409);
        assertThat(blocked.getResponse().getContentAsString()).contains("Bu telefonla kayıtlı müşteri var");

        // V30 eşsiz index'i düşürdüğü için bilinçli yinelenen kayıt gerçekten yazılabilmeli.
        MvcResult allowed = postCustomer(Map.of(
                "firstName", "Ayse", "phone", "+90 532 111 22 33", "allowDuplicate", true));
        assertThat(allowed.getResponse().getStatus()).isEqualTo(200);

        txTemplate.executeWithoutResult(status ->
                assertThat(customerRepository.findBySalonIdAndPhoneNormalized(salonId, "+905321112233"))
                        .hasSize(2));
    }

    @Test
    @DisplayName("Yinelenen müşteri ucu grupları döner")
    void duplicatesEndpointReportsTheGroup() throws Exception {
        postCustomer(Map.of("firstName", "Ayşe", "phone", "05321112233"));
        postCustomer(Map.of("firstName", "Ayse", "phone", "+905321112233", "allowDuplicate", true));

        MvcResult result = mockMvc.perform(get("/api/customers/duplicates")
                        .with(authentication(manager))
                        .header("X-Salon-Slug", slug))
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        assertThat(data).hasSize(1);
        assertThat(data.get(0).get("normalizedPhone").asText()).isEqualTo("+905321112233");
        assertThat(data.get(0).get("members")).hasSize(2);
    }

    /** Panel regex'i sabit hatları haksız yere reddediyordu; ortak kısıt bunu düzeltti. */
    @Test
    @DisplayName("Sabit hat ve yabancı numara artık kabul edilir")
    void landlineAndForeignNumbersAreAccepted() throws Exception {
        assertThat(postCustomer(Map.of("firstName", "Sabit", "phone", "0212 333 44 55"))
                .getResponse().getStatus()).isEqualTo(200);
        assertThat(postCustomer(Map.of("firstName", "Hans", "phone", "+4915112345678"))
                .getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Çözümlenemeyen numara reddedilir")
    void garbagePhoneIsRejected() throws Exception {
        assertThat(postCustomer(Map.of("firstName", "Cop", "phone", "abc123"))
                .getResponse().getStatus()).isEqualTo(400);
        assertThat(postCustomer(Map.of("firstName", "Cop", "phone", "1234567"))
                .getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("Telefonu boş müşteri eklenebilir")
    void blankPhoneIsStillAllowed() throws Exception {
        assertThat(postCustomer(Map.of("firstName", "Telefonsuz", "phone", ""))
                .getResponse().getStatus()).isEqualTo(200);
    }
}
