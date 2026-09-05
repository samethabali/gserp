package com.gscrm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gscrm.model.Organization;
import com.gscrm.model.Salon;
import com.gscrm.model.User;
import com.gscrm.model.enums.OrganizationType;
import com.gscrm.model.enums.UserRole;
import com.gscrm.repository.OrganizationRepository;
import com.gscrm.repository.OrganizationSubscriptionRepository;
import com.gscrm.repository.SalonRepository;
import com.gscrm.repository.SubscriptionPlanRepository;
import com.gscrm.repository.UserRepository;
import com.gscrm.security.AuthenticatedUser;
import com.gscrm.support.SubscriptionFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Personel eklendiğinde giriş hesabının da açıldığını ve üretilen geçici parolanın
 * gerçekten çalıştığını sınar.
 *
 * <p>Parola hash'lenerek saklandığı için yanıtta dönen değerin doğruluğu ancak
 * kaydedilen hash'e karşı doğrulanarak görülebilir; servis birim testi sahte
 * encoder ile bunu gösteremez.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Personel giriş hesabı")
class StaffAccountProvisioningIT {

    private final String slug = "staff-acc-" + java.util.UUID.randomUUID().toString().substring(0, 8);

    /*
     * Testler aynı veritabanını paylaşıyor ve personel kullanıcı adları sistem
     * genelinde tekil; bu yüzden her test kendi personel adını kullanır, aksi
     * hâlde üretilen ad diğer testlerin bıraktığı kayıtlara göre numaralanır.
     */

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TransactionTemplate txTemplate;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private SalonRepository salonRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private OrganizationSubscriptionRepository subscriptionRepository;
    @Autowired private SubscriptionPlanRepository subscriptionPlanRepository;

    private Long orgId;
    private Long salonId;
    private UsernamePasswordAuthenticationToken manager;

    @BeforeEach
    void seed() {
        txTemplate.executeWithoutResult(status -> {
            LocalDateTime now = LocalDateTime.now();
            Organization org = organizationRepository.save(Organization.builder()
                    .name("Hesap Org").type(OrganizationType.STANDALONE)
                    .active(true).loyaltyPolicy("SALON").createdAt(now).build());
            orgId = org.getId();
            salonId = salonRepository.save(Salon.builder()
                    .organizationId(orgId).slug(slug).name("Hesap Salonu")
                    .timezone("Europe/Istanbul").active(true).createdAt(now).build()).getId();
            SubscriptionFixtures.seedTrial(subscriptionRepository, subscriptionPlanRepository, orgId);
        });

        AuthenticatedUser user = new AuthenticatedUser(
                9100L, "yonetici", "", true, UserRole.BRANCH_MANAGER,
                null, null, salonId, orgId, false, 0,
                List.of(new SimpleGrantedAuthority("ROLE_BRANCH_MANAGER")));
        manager = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    @Test
    @DisplayName("Personel eklendiğinde hesap ve geçici parola üretilir")
    void createsAccountWithTemporaryPassword() throws Exception {
        JsonNode data = createStaff("Ayşe Yılmaz", "SPECIALIST");

        JsonNode account = data.path("account");
        assertThat(account.isMissingNode() || account.isNull())
                .as("hesap açılmalı :: %s", data)
                .isFalse();
        assertThat(account.path("username").asText()).isEqualTo("ayse.yilmaz");
        assertThat(account.path("mustChangePassword").asBoolean()).isTrue();

        String temporaryPassword = account.path("temporaryPassword").asText();
        assertThat(temporaryPassword).hasSizeGreaterThanOrEqualTo(8);

        User saved = userRepository.findByIdAndSalonId(account.path("userId").asLong(), salonId).orElseThrow();
        assertThat(saved.getStaffId()).isEqualTo(data.path("staff").path("id").asLong());
        assertThat(saved.getRole()).isEqualTo(UserRole.SPECIALIST);
        assertThat(saved.isMustChangePassword())
                .as("ilk giriş parola değişimine zorlamalı")
                .isTrue();
        assertThat(passwordEncoder.matches(temporaryPassword, saved.getPasswordHash()))
                .as("yanıtta dönen geçici parola gerçekten giriş yapmalı")
                .isTrue();
    }

    @Test
    @DisplayName("Aynı isimli ikinci personel çakışmayan kullanıcı adı alır")
    void generatesUniqueUsernames() throws Exception {
        String first = createStaff("Merve Şahin", "SPECIALIST").path("account").path("username").asText();
        String second = createStaff("Merve Şahin", "RECEPTIONIST").path("account").path("username").asText();

        assertThat(first).isEqualTo("merve.sahin");
        assertThat(second).isEqualTo("merve.sahin2");
    }

    @Test
    @DisplayName("Personel rolü hesabın rolüne çevrilir")
    void mapsStaffRoleToUserRole() throws Exception {
        JsonNode data = createStaff("Fatma Öz", "ADMIN");
        User saved = userRepository.findByIdAndSalonId(
                data.path("account").path("userId").asLong(), salonId).orElseThrow();
        assertThat(saved.getRole()).isEqualTo(UserRole.BRANCH_MANAGER);
    }

    @Test
    @DisplayName("createAccount=false verilince hesap açılmaz")
    void skipsAccountWhenNotRequested() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/staff?createAccount=false")
                        .with(authentication(manager)).with(csrf())
                        .header("X-Salon-Slug", slug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Hesapsız Uzman", "role", "SPECIALIST",
                                "colorHex", "#112233", "active", true))))
                .andReturn();

        JsonNode data = readData(result);
        assertThat(data.path("staff").path("id").asLong()).isPositive();
        assertThat(data.path("account").isNull()).isTrue();
        assertThat(userRepository.findBySalonIdAndStaffId(salonId, data.path("staff").path("id").asLong()))
                .isEmpty();
    }

    @Test
    @DisplayName("Personel pasife alınınca hesabı da kapanır")
    void disablesAccountWhenStaffDeactivated() throws Exception {
        JsonNode data = createStaff("Zeynep Ak", "SPECIALIST");
        long staffId = data.path("staff").path("id").asLong();

        mockMvc.perform(put("/api/staff/" + staffId)
                        .with(authentication(manager)).with(csrf())
                        .header("X-Salon-Slug", slug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Zeynep Ak", "role", "SPECIALIST",
                                "colorHex", "#112233", "active", false))))
                .andReturn();

        User saved = userRepository.findBySalonIdAndStaffId(salonId, staffId).orElseThrow();
        assertThat(saved.isEnabled())
                .as("işten ayrılan personelin girişi kapanmalı")
                .isFalse();
    }

    @Test
    @DisplayName("Parola sıfırlama yeni geçici parola üretir ve oturumları düşürür")
    void resetIssuesNewTemporaryPassword() throws Exception {
        JsonNode created = createStaff("Elif Kaya", "SPECIALIST");
        long staffId = created.path("staff").path("id").asLong();
        String firstPassword = created.path("account").path("temporaryPassword").asText();
        int versionBefore = userRepository.findBySalonIdAndStaffId(salonId, staffId).orElseThrow()
                .getTokenVersion();

        MvcResult result = mockMvc.perform(post("/api/staff/" + staffId + "/account/reset-password")
                        .with(authentication(manager)).with(csrf())
                        .header("X-Salon-Slug", slug))
                .andReturn();

        String newPassword = readData(result).path("temporaryPassword").asText();
        assertThat(newPassword).isNotEqualTo(firstPassword);

        User saved = userRepository.findBySalonIdAndStaffId(salonId, staffId).orElseThrow();
        assertThat(passwordEncoder.matches(newPassword, saved.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches(firstPassword, saved.getPasswordHash()))
                .as("eski parola artık geçmemeli")
                .isFalse();
        assertThat(saved.getTokenVersion())
                .as("açık oturumlar düşmeli")
                .isGreaterThan(versionBefore);
    }

    private JsonNode createStaff(String name, String role) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/staff")
                        .with(authentication(manager)).with(csrf())
                        .header("X-Salon-Slug", slug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name, "role", role,
                                "colorHex", "#123456", "active", true))))
                .andReturn();
        return readData(result);
    }

    private JsonNode readData(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        assertThat(result.getResponse().getStatus())
                .as("HTTP %s :: %s", result.getResponse().getStatus(), body)
                .isEqualTo(200);
        JsonNode json = objectMapper.readTree(body);
        assertThat(json.path("success").asBoolean()).as("başarılı dönmeli :: %s", body).isTrue();
        return json.path("data");
    }
}
