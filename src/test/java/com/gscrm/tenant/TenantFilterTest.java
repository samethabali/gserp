package com.gscrm.tenant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TenantFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void defaultSlugAllowsBookingServices() throws Exception {
        mockMvc.perform(get("/api/booking/services")
                        .header("X-Salon-Slug", "default"))
                .andExpect(status().isOk());
    }

    @Test
    void unknownSlugReturns404() throws Exception {
        mockMvc.perform(get("/api/booking/services")
                        .header("X-Salon-Slug", "does-not-exist-xyz"))
                .andExpect(status().isNotFound());
    }
}
