package com.gserp.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gserp.dto.request.AppointmentCreateRequest;
import com.gserp.dto.response.AppointmentResponse;
import com.gserp.service.AppointmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AppointmentService appointmentService;

    @Test
    void requestEndpointReturnsPendingApprovalMessage() throws Exception {
        when(appointmentService.createRequest(any())).thenReturn(
                AppointmentResponse.builder().id(42L).build());

        AppointmentCreateRequest req = AppointmentCreateRequest.builder()
                .customerName("Test Müşteri")
                .customerPhone("05551234567")
                .staffId(1L)
                .serviceId(1L)
                .startTime(LocalDateTime.of(2026, 6, 20, 10, 0))
                .build();

        mockMvc.perform(post("/api/booking/request")
                        .header("X-Salon-Slug", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Randevu isteğiniz alındı, salon onayı bekleniyor"))
                .andExpect(jsonPath("$.data.id").value(42));
    }
}
