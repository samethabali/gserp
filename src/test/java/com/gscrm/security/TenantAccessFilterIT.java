package com.gscrm.security;

import com.gscrm.model.Organization;
import com.gscrm.model.Salon;
import com.gscrm.model.enums.OrganizationType;
import com.gscrm.model.enums.UserRole;
import com.gscrm.repository.OrganizationRepository;
import com.gscrm.repository.SalonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TenantAccessFilter davranış testi: bir salona bağlı kullanıcı, X-Salon-Slug
 * header'ını başka bir salona çevirerek o salonun verisine erişemez.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TenantAccessFilterIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private SalonRepository salonRepository;

    private Long salonAId;
    private String salonASlug;
    private String salonBSlug;

    @BeforeEach
    void seedTwoSalons() {
        LocalDateTime now = LocalDateTime.now();
        Organization orgA = organizationRepository.save(Organization.builder()
                .name("Org A").type(OrganizationType.STANDALONE).active(true)
                .loyaltyPolicy("SALON").createdAt(now).build());
        Organization orgB = organizationRepository.save(Organization.builder()
                .name("Org B").type(OrganizationType.STANDALONE).active(true)
                .loyaltyPolicy("SALON").createdAt(now).build());

        Salon salonA = salonRepository.save(Salon.builder()
                .organizationId(orgA.getId()).slug("tenant-a").name("Salon A")
                .timezone("Europe/Istanbul").active(true).createdAt(now).build());
        Salon salonB = salonRepository.save(Salon.builder()
                .organizationId(orgB.getId()).slug("tenant-b").name("Salon B")
                .timezone("Europe/Istanbul").active(true).createdAt(now).build());

        salonAId = salonA.getId();
        salonASlug = salonA.getSlug();
        salonBSlug = salonB.getSlug();
    }

    private UsernamePasswordAuthenticationToken receptionistOfSalonA() {
        AuthenticatedUser user = new AuthenticatedUser(
                501L, "reception@a", "", true, UserRole.RECEPTIONIST,
                null, null, salonAId, null, false,
                List.of(new SimpleGrantedAuthority("ROLE_RECEPTIONIST")));
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    @Test
    void receptionistCannotAccessOtherSalonViaSlugHeader() throws Exception {
        mockMvc.perform(get("/api/customers")
                        .with(authentication(receptionistOfSalonA()))
                        .header("X-Salon-Slug", salonBSlug))
                .andExpect(status().isForbidden());
    }

    @Test
    void receptionistCanAccessOwnSalon() throws Exception {
        mockMvc.perform(get("/api/customers")
                        .with(authentication(receptionistOfSalonA()))
                        .header("X-Salon-Slug", salonASlug))
                .andExpect(status().isOk());
    }
}
