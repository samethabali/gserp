package com.gscrm.service;

import com.gscrm.dto.request.TenantProvisionRequest;
import com.gscrm.dto.response.TenantProvisionResponse;
import com.gscrm.model.*;
import com.gscrm.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalonProvisioningServiceTest {

    @Mock private OrganizationRepository organizationRepository;
    @Mock private SalonRepository salonRepository;
    @Mock private SalonSettingRepository salonSettingRepository;
    @Mock private UserRepository userRepository;
    @Mock private OnboardingStateRepository onboardingStateRepository;
    @Mock private SubscriptionPlanRepository subscriptionPlanRepository;
    @Mock private OrganizationSubscriptionRepository organizationSubscriptionRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ServiceTemplateService serviceTemplateService;
    @Mock private StaffRepository staffRepository;
    @Mock private CustomerRepository customerRepository;

    @InjectMocks
    private SalonProvisioningService salonProvisioningService;

    @Test
    void provision_seedsServiceTemplateForNewSalon() {
        when(salonRepository.findBySlugAndActiveTrue("yeni-salon")).thenReturn(Optional.empty());
        when(organizationRepository.save(any())).thenAnswer(inv -> {
            Organization o = inv.getArgument(0);
            o.setId(10L);
            return o;
        });
        when(salonRepository.save(any())).thenAnswer(inv -> {
            Salon s = inv.getArgument(0);
            s.setId(20L);
            return s;
        });
        when(userRepository.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(30L);
            return u;
        });
        when(onboardingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(subscriptionPlanRepository.findByCodeAndActiveTrue("SOLO"))
                .thenReturn(Optional.of(SubscriptionPlan.builder()
                        .id(1L).code("SOLO").name("Solo").priceMonthly(BigDecimal.TEN).build()));
        when(organizationSubscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(serviceTemplateService.seedHairAndSkinMenu(20L))
                .thenReturn(List.of(ServiceDefinition.builder().name("Saç Kesim").build()));

        TenantProvisionRequest request = new TenantProvisionRequest();
        request.setOrganizationName("Test Org");
        request.setSalonName("Test Salon");
        request.setSalonSlug("yeni-salon");
        request.setAdminUsername("admin@test");
        request.setAdminPassword("password123");

        TenantProvisionResponse response = salonProvisioningService.provision(request);

        assertThat(response.getSalonSlug()).isEqualTo("yeni-salon");
        verify(serviceTemplateService).seedHairAndSkinMenu(20L);
    }
}
