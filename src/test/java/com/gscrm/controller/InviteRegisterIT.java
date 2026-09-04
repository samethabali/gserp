package com.gscrm.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class InviteRegisterIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void registerWithoutInviteCodeIsForbidden() throws Exception {
        mockMvc.perform(post("/api/onboarding/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationName": "Test Org",
                                  "salonName": "Test Salon",
                                  "salonSlug": "invite-it-salon",
                                  "adminUsername": "admininvite",
                                  "adminPassword": "password123"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void registerWithInvalidInviteCodeIsForbidden() throws Exception {
        mockMvc.perform(post("/api/onboarding/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "inviteCode": "GSCRM-NOPE-NOPE",
                                  "organizationName": "Test Org",
                                  "salonName": "Test Salon",
                                  "salonSlug": "invite-it-salon-2",
                                  "adminUsername": "admininvite2",
                                  "adminPassword": "password123"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}
