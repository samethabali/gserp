package com.gscrm.tenant;

import com.gscrm.model.enums.UserRole;
import com.gscrm.security.AuthenticatedUser;
import com.gscrm.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TenantFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Test
    void defaultSlugAllowsBookingServices() throws Exception {
        mockMvc.perform(get("/api/booking/services")
                        .header("X-Salon-Slug", "default"))
                .andExpect(status().isOk());
    }

    @Test
    void unknownSlugReturns404OnApi() throws Exception {
        mockMvc.perform(get("/api/booking/services")
                        .header("X-Salon-Slug", "does-not-exist-xyz"))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingTenantOnApiReturns400() throws Exception {
        mockMvc.perform(get("/api/booking/services"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publicBookingPathResolvesSalon() throws Exception {
        mockMvc.perform(get("/b/default"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/default"))
                .andExpect(status().isOk());
    }

    @Test
    void bookingWithoutSlugRendersNoSalonPage() throws Exception {
        mockMvc.perform(get("/booking"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("İşletme Seçilmedi")));
    }

    @Test
    void sessionTenantResolvesSalon() throws Exception {
        mockMvc.perform(get("/api/settings/public")
                        .sessionAttr(TenantContext.SESSION_AUTH_SALON_ID, 1L))
                .andExpect(status().isOk());
    }

    @Test
    void jwtBearerTokenResolvesSalonId() throws Exception {
        AuthenticatedUser user = new AuthenticatedUser(
                100L, "teststaff", "pass", true, UserRole.ADMIN,
                null, null, 1L, 1L, false, 0,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        String token = jwtService.generateToken(user);

        mockMvc.perform(get("/api/settings/public")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void mismatchBetweenAuthenticatedSalonAndExplicitSlugIsForbidden() throws Exception {
        // Salon 1'in slug'ı 'default'tur; istek açıkça başka bir slug gönderdiğinde 403 almalı.
        mockMvc.perform(get("/api/settings/public")
                        .sessionAttr(TenantContext.SESSION_AUTH_SALON_ID, 1L)
                        .header("X-Salon-Slug", "baska-salon"))
                .andExpect(status().isForbidden());
    }
}
