package com.gscrm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gscrm.dto.request.AppointmentCreateRequest;
import com.gscrm.dto.response.AppointmentResponse;
import com.gscrm.model.Staff;
import com.gscrm.model.enums.StaffRole;
import com.gscrm.repository.StaffRepository;
import com.gscrm.tenant.TenantContext;
import com.gscrm.service.AppointmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @MockBean
    private StaffRepository staffRepository;

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

    /**
     * Tuzak alan doluysa istek sessizce yutulur.
     *
     * <p>Yanıt normal başarı yanıtıdır: bota reddedildiğini söylemek, hangi sinyale
     * takıldığını öğrenip bir sonraki denemede atlamasını sağlardı. Önemli olan
     * hiçbir randevunun yazılmamasıdır.
     */
    @Test
    void honeypotFieldSilentlyDiscardsTheRequest() throws Exception {
        AppointmentCreateRequest req = AppointmentCreateRequest.builder()
                .customerName("Bot")
                .customerPhone("05551234567")
                .staffId(1L)
                .serviceId(1L)
                .startTime(LocalDateTime.of(2026, 6, 20, 10, 0))
                .website("http://spam.example")
                .build();

        mockMvc.perform(post("/api/booking/request")
                        .header("X-Salon-Slug", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(appointmentService, never()).createRequest(any());
    }

    /** Form insan hızının çok altında dolduruldu — aynı sessiz tuzak. */
    @Test
    void instantSubmissionSilentlyDiscardsTheRequest() throws Exception {
        AppointmentCreateRequest req = AppointmentCreateRequest.builder()
                .customerName("Bot")
                .customerPhone("05551234567")
                .staffId(1L)
                .serviceId(1L)
                .startTime(LocalDateTime.of(2026, 6, 20, 10, 0))
                .elapsedMs(120L)
                .build();

        mockMvc.perform(post("/api/booking/request")
                        .header("X-Salon-Slug", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        verify(appointmentService, never()).createRequest(any());
    }

    /** Panelin katı regex'i public uca taşındı: çözümlenemeyen numara artık reddedilir. */
    @Test
    void unparseablePhoneIsRejectedByValidation() throws Exception {
        AppointmentCreateRequest req = AppointmentCreateRequest.builder()
                .customerName("Test Müşteri")
                .customerPhone("1234567")
                .staffId(1L)
                .serviceId(1L)
                .startTime(LocalDateTime.of(2026, 6, 20, 10, 0))
                .build();

        mockMvc.perform(post("/api/booking/request")
                        .header("X-Salon-Slug", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());

        verify(appointmentService, never()).createRequest(any());
    }

    /**
     * Uc yalnizca aktif SPECIALIST donmelidir.
     *
     * <p>Regresyon: {@code PublicStaffResponse} personelin rolunu disariya
     * vermiyor, ama booking.js bir donem yaniti {@code role === 'SPECIALIST'}
     * ile suzuyordu. Filtre sunucudan istemciye kaymis oldugu icin uc rol
     * tasimayi birakinca arayuz her uzmani eledi ve online randevuda
     * "Uzman bulunamadi" yazdi. Bu test filtreyi sunucuda sabitler:
     * rol filtresiz sorguya donulurse resepsiyonist yanita sizar ve test kirilir.
     */
    @Test
    void staffEndpointReturnsOnlyActiveSpecialists() throws Exception {
        Staff specialist = Staff.builder()
                .id(7L).name("Uzman Ayse").role(StaffRole.SPECIALIST)
                .colorHex("#9b59b6").active(true).build();
        Staff receptionist = Staff.builder()
                .id(8L).name("Resepsiyon Ali").role(StaffRole.RECEPTIONIST)
                .colorHex("#333333").active(true).build();

        when(staffRepository.findBySalonIdAndActiveTrueAndRole(anyLong(), eq(StaffRole.SPECIALIST)))
                .thenReturn(List.of(specialist));
        // Rol filtresiz sorgu resepsiyonisti de dondurur; uc bunu kullanmamali.
        when(staffRepository.findBySalonIdAndActiveTrue(anyLong()))
                .thenReturn(List.of(specialist, receptionist));

        // addFilters = false oldugu icin TenantFilter calismaz; bagLami elle kuruyoruz.
        TenantContext.setSalonId(1L);
        try {
            mockMvc.perform(get("/api/booking/staff"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(7))
                .andExpect(jsonPath("$.data[0].name").value("Uzman Ayse"))
                // Rol disariya sizmamali: arayuz bu alana bir daha bel baglamasin.
                .andExpect(jsonPath("$.data[0].role").doesNotExist());
        } finally {
            TenantContext.clear();
        }
    }
}
