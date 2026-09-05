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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code app.public-base-url} yapılandırılmamışken randevu linki.
 *
 * <p>Varsayılan {@code http://localhost:8989} idi: env geçilmeyen her kurulumda
 * salon sahibine randevu adresi olarak localhost gösteriliyordu — paylaşması
 * beklenen link, hiç kimsede açılmayan bir adres. Artık adres isteğin kendisinden
 * türetilir; vekil arkasında da doğru olması için
 * {@code server.forward-headers-strategy: framework} açıktır.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.public-base-url=")
@Transactional
@DisplayName("Randevu linki — taban adres yapılandırılmamış")
class SalonSettingsBookingUrlFallbackIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private SalonRepository salonRepository;

    private final String slug = "fb-" + UUID.randomUUID().toString().substring(0, 8);

    @BeforeEach
    void seedTenant() {
        LocalDateTime now = LocalDateTime.now();
        Organization org = organizationRepository.save(Organization.builder()
                .name("Fallback Org").type(OrganizationType.STANDALONE)
                .active(true).loyaltyPolicy("SALON").createdAt(now).build());
        salonRepository.save(Salon.builder()
                .organizationId(org.getId()).slug(slug).name("Fallback Salonu")
                .timezone("Europe/Istanbul").active(true).createdAt(now).build());
    }

    @Test
    @DisplayName("link isteğin adresinden türetilir, localhost:8989 sabitine düşmez")
    void derivesBookingUrlFromRequest() throws Exception {
        mockMvc.perform(get("/api/settings")
                        .with(authentication(authFor()))
                        .header("X-Salon-Slug", slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookingUrl").value("http://localhost/" + slug));
    }

    /** Vekil arkasında dışarıdan görünen adres kullanılmalı. */
    @Test
    @DisplayName("X-Forwarded-* başlıkları dikkate alınır")
    void honoursForwardedHeaders() throws Exception {
        mockMvc.perform(get("/api/settings")
                        .with(authentication(authFor()))
                        .header("X-Salon-Slug", slug)
                        .header("X-Forwarded-Proto", "https")
                        .header("X-Forwarded-Host", "gscrm.avesitesi.xyz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookingUrl")
                        .value("https://gscrm.avesitesi.xyz/" + slug));
    }

    private UsernamePasswordAuthenticationToken authFor() {
        AuthenticatedUser user = new AuthenticatedUser(
                -1L, "fallback-admin", "", true, UserRole.PLATFORM_ADMIN,
                null, null, null, null, false, 0,
                List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN")));
        return UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
    }
}
