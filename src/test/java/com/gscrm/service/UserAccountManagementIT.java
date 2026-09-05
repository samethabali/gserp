package com.gscrm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gscrm.model.Organization;
import com.gscrm.model.Salon;
import com.gscrm.model.Staff;
import com.gscrm.model.User;
import com.gscrm.model.enums.OrganizationType;
import com.gscrm.model.enums.StaffRole;
import com.gscrm.model.enums.UserRole;
import com.gscrm.repository.OrganizationRepository;
import com.gscrm.repository.OrganizationSubscriptionRepository;
import com.gscrm.repository.SalonRepository;
import com.gscrm.repository.StaffRepository;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Kullanıcı yönetimi ekranının arkasındaki uçları sınar.
 *
 * <p>Uç, rolü hiç sınırlamadığı için salon yöneticisi kendine PLATFORM_ADMIN
 * hesabı açıp bütün kiracılara erişebiliyordu; ayrıca {@code staffId} doğrulanmadan
 * yazıldığı için başka salonun personeline hesap bağlanabiliyordu. Testler bu iki
 * kapının kapalı kaldığını ve normal akışın çalıştığını gösterir.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Kullanıcı hesabı yönetimi")
class UserAccountManagementIT {

    private final String slug = "user-mgmt-" + UUID.randomUUID().toString().substring(0, 8);
    private final String otherSlug = "user-mgmt-x-" + UUID.randomUUID().toString().substring(0, 8);

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TransactionTemplate txTemplate;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private SalonRepository salonRepository;
    @Autowired private StaffRepository staffRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private OrganizationSubscriptionRepository subscriptionRepository;
    @Autowired private SubscriptionPlanRepository subscriptionPlanRepository;

    private Long orgId;
    private Long salonId;
    private Long staffId;
    private Long foreignStaffId;
    private UsernamePasswordAuthenticationToken manager;

    /** Kullanıcı adları sistem genelinde tekil; her test kendi ön ekini kullanır. */
    private String uniqueUsername(String prefix) {
        return prefix + "." + UUID.randomUUID().toString().substring(0, 8);
    }

    @BeforeEach
    void seed() {
        txTemplate.executeWithoutResult(status -> {
            LocalDateTime now = LocalDateTime.now();
            Organization org = organizationRepository.save(Organization.builder()
                    .name("Kullanıcı Org").type(OrganizationType.STANDALONE)
                    .active(true).loyaltyPolicy("SALON").createdAt(now).build());
            orgId = org.getId();
            salonId = salonRepository.save(Salon.builder()
                    .organizationId(orgId).slug(slug).name("Kullanıcı Salonu")
                    .timezone("Europe/Istanbul").active(true).createdAt(now).build()).getId();
            SubscriptionFixtures.seedTrial(subscriptionRepository, subscriptionPlanRepository, orgId);
            staffId = staffRepository.save(Staff.builder()
                    .salonId(salonId).name("Deniz Uzman").role(StaffRole.SPECIALIST)
                    .colorHex("#123456").active(true).createdAt(now).build()).getId();

            Organization otherOrg = organizationRepository.save(Organization.builder()
                    .name("Komşu Org").type(OrganizationType.STANDALONE)
                    .active(true).loyaltyPolicy("SALON").createdAt(now).build());
            Long otherSalonId = salonRepository.save(Salon.builder()
                    .organizationId(otherOrg.getId()).slug(otherSlug).name("Komşu Salon")
                    .timezone("Europe/Istanbul").active(true).createdAt(now).build()).getId();
            SubscriptionFixtures.seedTrial(subscriptionRepository, subscriptionPlanRepository, otherOrg.getId());
            foreignStaffId = staffRepository.save(Staff.builder()
                    .salonId(otherSalonId).name("Komşu Uzman").role(StaffRole.SPECIALIST)
                    .colorHex("#654321").active(true).createdAt(now).build()).getId();
        });

        manager = authFor(9200L, "yonetici", UserRole.BRANCH_MANAGER);
    }

    private UsernamePasswordAuthenticationToken authFor(Long id, String username, UserRole role) {
        AuthenticatedUser user = new AuthenticatedUser(
                id, username, "", true, role, null, null, salonId, orgId, false, 0,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    @Test
    @DisplayName("Parola boş bırakılınca sunucu çalışan bir geçici parola üretir")
    void generatesTemporaryPasswordWhenBlank() throws Exception {
        String username = uniqueUsername("resepsiyon");
        MvcResult result = create(Map.of("username", username, "role", "RECEPTIONIST"));
        JsonNode data = readData(result);

        String temporaryPassword = data.path("temporaryPassword").asText();
        assertThat(temporaryPassword).hasSizeGreaterThanOrEqualTo(8);
        assertThat(data.path("mustChangePassword").asBoolean()).isTrue();

        User saved = userRepository.findByIdAndSalonId(data.path("id").asLong(), salonId).orElseThrow();
        assertThat(saved.getRole()).isEqualTo(UserRole.RECEPTIONIST);
        assertThat(passwordEncoder.matches(temporaryPassword, saved.getPasswordHash()))
                .as("yanıtta dönen geçici parola gerçekten giriş yapmalı")
                .isTrue();
    }

    @Test
    @DisplayName("Yanıt parola hash'ini taşımaz")
    void doesNotLeakPasswordHash() throws Exception {
        MvcResult result = create(Map.of("username", uniqueUsername("gizli"), "role", "RECEPTIONIST"));
        assertThat(result.getResponse().getContentAsString()).doesNotContain("passwordHash");
    }

    @Test
    @DisplayName("Şube yöneticisi PLATFORM_ADMIN hesabı açamaz")
    void rejectsPlatformAdminRole() throws Exception {
        String username = uniqueUsername("sahte.admin");
        MvcResult result = create(Map.of("username", username, "role", "PLATFORM_ADMIN"));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(userRepository.findStaffByUsername(username)).isEmpty();
    }

    @Test
    @DisplayName("Şube yöneticisi kendi yetkisini aşan rol atayamaz")
    void rejectsRoleAboveOwnRank() throws Exception {
        MvcResult result = create(Map.of("username", uniqueUsername("patron"), "role", "ORG_OWNER"));
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("Uzman hesabı personel kaydı olmadan açılmaz")
    void requiresStaffForSpecialist() throws Exception {
        MvcResult result = create(Map.of("username", uniqueUsername("uzman"), "role", "SPECIALIST"));
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("Başka salonun personeline hesap bağlanamaz")
    void rejectsForeignStaffLink() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("username", uniqueUsername("sizinti"));
        body.put("role", "SPECIALIST");
        body.put("staffId", foreignStaffId);

        MvcResult result = create(body);
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("Aynı personele ikinci hesap açılmaz")
    void rejectsSecondAccountForSameStaff() throws Exception {
        Map<String, Object> first = new HashMap<>();
        first.put("username", uniqueUsername("deniz"));
        first.put("role", "SPECIALIST");
        first.put("staffId", staffId);
        readData(create(first));

        Map<String, Object> second = new HashMap<>();
        second.put("username", uniqueUsername("deniz2"));
        second.put("role", "SPECIALIST");
        second.put("staffId", staffId);
        assertThat(create(second).getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("Geçersiz kullanıcı adı açıklamalı 400 döner")
    void rejectsInvalidUsername() throws Exception {
        MvcResult result = create(Map.of("username", "Ayşe Yılmaz", "role", "RECEPTIONIST"));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).path("message").asText())
                .as("mesaj kuralı anlatmalı, boş geçilmemeli")
                .contains("küçük harf");
    }

    @Test
    @DisplayName("Yönetici kendi hesabını devre dışı bırakamaz")
    void rejectsSelfDisable() throws Exception {
        JsonNode created = readData(create(Map.of(
                "username", uniqueUsername("kendisi"), "role", "BRANCH_MANAGER")));
        long userId = created.path("id").asLong();

        MvcResult result = mockMvc.perform(patch("/api/users/" + userId + "/enabled")
                        .with(authentication(authFor(userId, created.path("username").asText(),
                                UserRole.BRANCH_MANAGER)))
                        .with(csrf())
                        .header("X-Salon-Slug", slug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", false))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(userRepository.findByIdAndSalonId(userId, salonId).orElseThrow().isEnabled()).isTrue();
    }

    @Test
    @DisplayName("Atanabilir roller yetkiyle sınırlıdır")
    void assignableRolesFollowActorRank() throws Exception {
        MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/users/assignable-roles")
                        .with(authentication(manager))
                        .header("X-Salon-Slug", slug))
                .andReturn();

        JsonNode data = readData(result);
        List<String> roles = objectMapper.convertValue(data, List.class);
        assertThat(roles).containsExactly("BRANCH_MANAGER", "RECEPTIONIST", "SPECIALIST");
    }

    private MvcResult create(Map<String, Object> body) throws Exception {
        return mockMvc.perform(post("/api/users")
                        .with(authentication(manager)).with(csrf())
                        .header("X-Salon-Slug", slug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn();
    }

    private JsonNode readData(MvcResult result) throws Exception {
        String responseBody = result.getResponse().getContentAsString();
        assertThat(result.getResponse().getStatus())
                .as("HTTP %s :: %s", result.getResponse().getStatus(), responseBody)
                .isEqualTo(200);
        JsonNode json = objectMapper.readTree(responseBody);
        assertThat(json.path("success").asBoolean()).as("başarılı dönmeli :: %s", responseBody).isTrue();
        return json.path("data");
    }
}
