package com.gscrm.controller;

import com.gscrm.model.Organization;
import com.gscrm.model.Salon;
import com.gscrm.model.enums.OrganizationType;
import com.gscrm.model.enums.UserRole;
import com.gscrm.repository.OrganizationRepository;
import com.gscrm.repository.SalonRepository;
import com.gscrm.security.AuthenticatedUser;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.http.MediaType;

/**
 * İşletmenin randevu linki panelden okunabilmeli.
 *
 * <p>Link yalnızca kayıt sihirbazında bir kez üretiliyordu; o ekran geçildikten
 * sonra salon sahibinin kendi randevu adresini bulabileceği hiçbir yer yoktu.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.public-base-url=https://gscrm.example.test/")
@Transactional
@DisplayName("Randevu linki")
class SalonSettingsBookingUrlIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private SalonRepository salonRepository;

    private final String slug = "link-" + UUID.randomUUID().toString().substring(0, 8);
    private Long salonId;
    private Long orgId;

    @BeforeEach
    void seedTenant() {
        LocalDateTime now = LocalDateTime.now();
        Organization org = organizationRepository.save(Organization.builder()
                .name("Link Org").type(OrganizationType.STANDALONE)
                .active(true).loyaltyPolicy("SALON").createdAt(now).build());
        orgId = org.getId();
        salonId = salonRepository.save(Salon.builder()
                .organizationId(orgId).slug(slug).name("Link Salonu")
                .timezone("Europe/Istanbul").active(true).createdAt(now).build()).getId();
    }

    @Test
    @DisplayName("yönetim ayarları randevu linkini döndürür")
    void managementSettingsExposeBookingUrl() throws Exception {
        mockMvc.perform(get("/api/settings")
                        .with(authentication(authFor(UserRole.PLATFORM_ADMIN)))
                        .header("X-Salon-Slug", slug))
                .andExpect(status().isOk())
                // Taban adresin sonundaki eğik çizgi tekrarlanmamalı.
                .andExpect(jsonPath("$.data.bookingUrl")
                        .value("https://gscrm.example.test/" + slug));
    }

    /** Public uç kiracıya özel bir alan sızdırmamalı: link yalnızca panelde. */
    @Test
    @DisplayName("public ayarlar randevu linki taşımaz")
    void publicSettingsDoNotExposeBookingUrl() throws Exception {
        mockMvc.perform(get("/api/settings/public").header("X-Salon-Slug", slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookingUrl").doesNotExist());
    }

    @Test
    @DisplayName("salon adı değişince randevu adresi de değişir")
    void salonNameUpdatesBookingSlug() throws Exception {
        mockMvc.perform(put("/api/settings")
                        .with(authentication(authFor(UserRole.PLATFORM_ADMIN)))
                        .with(csrf())
                        .header("X-Salon-Slug", slug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Işıl Güzellik Merkezi\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/settings")
                        .with(authentication(authFor(UserRole.BRANCH_MANAGER)))
                        .header("X-Salon-Slug", "isil-guzellik-merkezi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookingUrl")
                        .value("https://gscrm.example.test/isil-guzellik-merkezi"));
    }

    private UsernamePasswordAuthenticationToken authFor(UserRole role) {
        AuthenticatedUser user = new AuthenticatedUser(
                8900L, "link-" + role.name().toLowerCase(), "", true, role,
                null, null, salonId, orgId, false, 0,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }
}
