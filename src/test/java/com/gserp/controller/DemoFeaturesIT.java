package com.gserp.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gserp.dto.request.WhatsAppSettingsUpdateRequest;
import com.gserp.model.Appointment;
import com.gserp.model.Customer;
import com.gserp.model.ServiceDefinition;
import com.gserp.model.Staff;
import com.gserp.model.enums.AppointmentStatus;
import com.gserp.model.enums.ServiceCategory;
import com.gserp.model.enums.StaffRole;
import com.gserp.repository.AppointmentRepository;
import com.gserp.repository.CustomerRepository;
import com.gserp.repository.ServiceDefinitionRepository;
import com.gserp.repository.StaffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DemoFeaturesIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private ServiceDefinitionRepository serviceDefinitionRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private CustomerRepository customerRepository;

    private Long staffId;
    private Long serviceId;

    @BeforeEach
    void seedAppointments() {
        LocalDateTime now = LocalDateTime.now();
        staffId = staffRepository.save(Staff.builder()
                .salonId(1L)
                .name("Demo Uzman")
                .role(StaffRole.SPECIALIST)
                .active(true)
                .build()).getId();
        serviceId = serviceDefinitionRepository.save(ServiceDefinition.builder()
                .salonId(1L)
                .name("Demo Hizmet")
                .durationMinutes(60)
                .basePrice(BigDecimal.valueOf(200))
                .category(ServiceCategory.HAIR)
                .active(true)
                .requiresResource(false)
                .build()).getId();

        customerRepository.save(Customer.builder()
                .salonId(1L)
                .firstName("Merve")
                .lastName("Aksoy")
                .phone("05321112233")
                .build());

        saveAppointment("Merve Aksoy", "05321112233", now.minusDays(1));
        saveAppointment("Merve Aksoy", "05321112233", now.minusDays(2));
        saveAppointment("Selin Yıldız", "05332223344", now.minusHours(3));
    }

    @Test
    @WithMockUser(username = "admin", roles = "BRANCH_MANAGER")
    void recentCustomersEndpointReturnsDistinctPhones() throws Exception {
        mockMvc.perform(get("/api/customers/recent")
                        .param("limit", "5")
                        .header("X-Salon-Slug", "default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].fullName").value("Selin Yıldız"))
                .andExpect(jsonPath("$.data[0].lastServiceName").value("Demo Hizmet"))
                .andExpect(jsonPath("$.data[1].fullName").value("Merve Aksoy"))
                .andExpect(jsonPath("$.data[1].id").isNumber());
    }

    @Test
    void recentCustomersRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/customers/recent")
                        .header("X-Salon-Slug", "default"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "admin", roles = "BRANCH_MANAGER")
    void whatsAppSettingsRoundTrip() throws Exception {
        mockMvc.perform(get("/api/settings/whatsapp")
                        .header("X-Salon-Slug", "default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.enabled").value(false));

        WhatsAppSettingsUpdateRequest update = new WhatsAppSettingsUpdateRequest();
        update.setEnabled(true);
        update.setToken("test-token");
        update.setPhoneNumberId("phone-id-1");
        update.setSalonPhoneE164("+905551112233");

        mockMvc.perform(put("/api/settings/whatsapp")
                        .with(csrf())
                        .header("X-Salon-Slug", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.tokenConfigured").value(true))
                .andExpect(jsonPath("$.data.phoneNumberId").value("phone-id-1"))
                .andExpect(jsonPath("$.data.salonPhoneE164").value("+905551112233"));
    }

    private void saveAppointment(String name, String phone, LocalDateTime start) {
        LocalDateTime now = LocalDateTime.now();
        appointmentRepository.save(Appointment.builder()
                .salonId(1L)
                .customerName(name)
                .customerPhone(phone)
                .staffId(staffId)
                .serviceId(serviceId)
                .startTime(start)
                .endTime(start.plusHours(1))
                .status(AppointmentStatus.COMPLETED)
                .basePrice(BigDecimal.valueOf(200))
                .adjustment(BigDecimal.ZERO)
                .finalPrice(BigDecimal.valueOf(200))
                .createdAt(now)
                .updatedAt(now)
                .build());
    }
}
