package com.gscrm.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gscrm.model.Appointment;
import com.gscrm.model.Organization;
import com.gscrm.model.Salon;
import com.gscrm.model.ServiceDefinition;
import com.gscrm.model.Staff;
import com.gscrm.model.enums.AppointmentStatus;
import com.gscrm.model.enums.OrganizationType;
import com.gscrm.model.enums.ServiceCategory;
import com.gscrm.model.enums.StaffRole;
import com.gscrm.model.enums.UserRole;
import com.gscrm.repository.AppointmentRepository;
import com.gscrm.repository.OrganizationRepository;
import com.gscrm.repository.SalonRepository;
import com.gscrm.repository.ServiceDefinitionRepository;
import com.gscrm.repository.StaffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FranchiseIsolationIT {

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

    @Autowired
    private AppointmentRepository appointmentRepository;

    private Long kadikoySalonId;
    private Long besiktasAppointmentId;

    @BeforeEach
    void seedFranchise() {
        LocalDateTime now = LocalDateTime.now();
        Organization org = organizationRepository.save(Organization.builder()
                .name("Belleza Test")
                .type(OrganizationType.FRANCHISE)
                .active(true)
                .loyaltyPolicy("ORG")
                .createdAt(now)
                .build());

        Salon kadikoy = salonRepository.save(Salon.builder()
                .organizationId(org.getId())
                .slug("test-kadikoy")
                .name("Kadıköy")
                .timezone("Europe/Istanbul")
                .active(true)
                .createdAt(now)
                .build());
        Salon besiktas = salonRepository.save(Salon.builder()
                .organizationId(org.getId())
                .slug("test-besiktas")
                .name("Beşiktaş")
                .timezone("Europe/Istanbul")
                .active(true)
                .createdAt(now)
                .build());
        kadikoySalonId = kadikoy.getId();

        Staff kadikoyStaff = staffRepository.save(Staff.builder()
                .salonId(kadikoy.getId())
                .name("Kadıköy Uzman")
                .role(StaffRole.SPECIALIST)
                .active(true)
                .build());
        Staff besiktasStaff = staffRepository.save(Staff.builder()
                .salonId(besiktas.getId())
                .name("Beşiktaş Uzman")
                .role(StaffRole.SPECIALIST)
                .active(true)
                .build());

        ServiceDefinition kadikoyService = serviceDefinitionRepository.save(ServiceDefinition.builder()
                .salonId(kadikoy.getId())
                .name("Saç Kesim")
                .durationMinutes(30)
                .basePrice(BigDecimal.valueOf(300))
                .category(ServiceCategory.HAIR)
                .active(true)
                .requiresResource(false)
                .build());
        ServiceDefinition besiktasService = serviceDefinitionRepository.save(ServiceDefinition.builder()
                .salonId(besiktas.getId())
                .name("Saç Kesim")
                .durationMinutes(30)
                .basePrice(BigDecimal.valueOf(350))
                .category(ServiceCategory.HAIR)
                .active(true)
                .requiresResource(false)
                .build());

        LocalDateTime start = LocalDateTime.of(2026, 12, 10, 14, 0);
        besiktasAppointmentId = appointmentRepository.save(Appointment.builder()
                .salonId(besiktas.getId())
                .customerName("Ali Veli")
                .customerPhone("05551112233")
                .staffId(besiktasStaff.getId())
                .serviceId(besiktasService.getId())
                .startTime(start)
                .endTime(start.plusMinutes(30))
                .status(AppointmentStatus.SCHEDULED)
                .basePrice(BigDecimal.valueOf(350))
                .adjustment(BigDecimal.ZERO)
                .finalPrice(BigDecimal.valueOf(350))
                .createdAt(now)
                .updatedAt(now)
                .build()).getId();

        // sanity: kadikoy has its own staff/service (unused in assertion)
        kadikoyStaff.getId();
        kadikoyService.getId();
    }

    @Test
    void branchManagerCannotChangeStatusOfOtherBranchAppointment() throws Exception {
        AuthenticatedUser manager = new AuthenticatedUser(
                99L, "manager@kadikoy", "", true, UserRole.BRANCH_MANAGER,
                null, null, kadikoySalonId, null, false,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_BRANCH_MANAGER")));
        var auth = new UsernamePasswordAuthenticationToken(manager, null, manager.getAuthorities());

        mockMvc.perform(patch("/api/appointments/" + besiktasAppointmentId + "/status")
                        .with(authentication(auth))
                        .with(csrf())
                        .header("X-Salon-Slug", "test-kadikoy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "COMPLETED"))))
                .andExpect(status().isForbidden());
    }
}
