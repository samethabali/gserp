package com.gscrm.security;

import com.gscrm.model.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void platformApiRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/platform/tenants"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void orgApiRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/org/salons")
                        .header("X-Salon-Slug", "default"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicBookingAllowed() throws Exception {
        mockMvc.perform(get("/api/booking/services")
                        .header("X-Salon-Slug", "default"))
                .andExpect(status().isOk());
    }

    @Test
    void authenticatedPasswordChangeRequiresCsrfToken() throws Exception {
        AuthenticatedUser principal = new AuthenticatedUser(
                -1L, "csrf-test", "", true, UserRole.PLATFORM_ADMIN,
                null, null, null, null, false, 0,
                List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN")));
        var auth = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());

        mockMvc.perform(post("/api/auth/change-password")
                        .with(authentication(auth))
                        .contentType("application/json")
                        .content("{\"currentPassword\":\"old-password\",\"newPassword\":\"new-password\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginWithoutRequiredFieldsIsRejectedBeforeAuthentication() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
