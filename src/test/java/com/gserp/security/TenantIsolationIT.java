package com.gserp.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gserp.dto.request.AppointmentCreateRequest;
import com.gserp.model.Organization;
import com.gserp.model.Salon;
import com.gserp.model.ServiceDefinition;
import com.gserp.model.Staff;
import com.gserp.model.enums.OrganizationType;
import com.gserp.model.enums.ServiceCategory;
import com.gserp.model.enums.StaffRole;
import com.gserp.repository.OrganizationRepository;
import com.gserp.repository.SalonRepository;
import com.gserp.repository.ServiceDefinitionRepository;
import com.gserp.repository.StaffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TenantIsolationIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private SalonRepository salonRepository;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private ServiceDefinitionRepository serviceDefinitionRepository;

    private Long defaultSalonServiceId;
    private Long otherSalonStaffId;

    @BeforeEach
    void seedSecondSalon() {
        LocalDateTime now = LocalDateTime.now();
        defaultSalonServiceId = serviceDefinitionRepository.save(ServiceDefinition.builder()
                .salonId(1L)
                .name("Test Haircut")
                .durationMinutes(30)
                .basePrice(java.math.BigDecimal.valueOf(100))
                .category(ServiceCategory.HAIR)
                .active(true)
                .requiresResource(false)
                .build()).getId();

        Organization org = organizationRepository.save(Organization.builder()
                .name("Test Org B")
                .type(OrganizationType.STANDALONE)
                .active(true)
                .loyaltyPolicy("SALON")
                .createdAt(now)
                .build());

        Salon salonB = salonRepository.save(Salon.builder()
                .organizationId(org.getId())
                .slug("salon-b")
                .name("Salon B")
                .timezone("Europe/Istanbul")
                .active(true)
                .createdAt(now)
                .build());

        Staff staffB = staffRepository.save(Staff.builder()
                .salonId(salonB.getId())
                .name("Staff B")
                .role(StaffRole.SPECIALIST)
                .active(true)
                .build());
        otherSalonStaffId = staffB.getId();
    }

    @Test
    void bookingRejectsStaffFromOtherSalon() throws Exception {
        AppointmentCreateRequest req = AppointmentCreateRequest.builder()
                .customerName("Cross Tenant")
                .customerPhone("05559998877")
                .staffId(otherSalonStaffId)
                .serviceId(defaultSalonServiceId)
                .startTime(LocalDateTime.of(2026, 12, 1, 10, 0))
                .consentTypes(java.util.List.of("PRIVACY"))
                .build();

        mockMvc.perform(post("/api/booking/request")
                        .header("X-Salon-Slug", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().is4xxClientError());
    }
}
