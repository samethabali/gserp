package com.gscrm.service;

import com.gscrm.dto.request.TenantProvisionRequest;
import com.gscrm.dto.response.TenantProvisionResponse;
import com.gscrm.model.*;
import com.gscrm.config.AppProperties;
import com.gscrm.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalonProvisioningServiceTest {

    @Mock private AppProperties appProperties;
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
        // Çakışma kontrolü aktiflikten bağımsız: askıya alınmış bir salonun slug'ı da dolu sayılır.
        when(salonRepository.existsBySlug("yeni-salon")).thenReturn(false);
        when(appProperties.getDefaultTrialDays()).thenReturn(14);
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
        // Deneme süresi artık koda gömülü 14 gün değil, yapılandırmadan geliyor.
        assertThat(response.getTrialEnd()).isAfter(LocalDateTime.now().plusDays(13));
        assertThat(response.getTrialEnd()).isBefore(LocalDateTime.now().plusDays(15));
    }

    @Test
    void provision_rejectsReservedSlug() {
        TenantProvisionRequest request = baseRequest("platform");
        // Rezerve slug kontrolü daha önce hiç yoktu: bir işletme "platform" adresini
        // alıp ürünün kendi yolunu gölgeleyebiliyordu.
        assertThatThrownBy(() -> salonProvisioningService.provision(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ayrılmış");
    }

    @Test
    void provision_rejectsSlugTakenByInactiveSalon() {
        when(salonRepository.existsBySlug("kapali-salon")).thenReturn(true);
        TenantProvisionRequest request = baseRequest("kapali-salon");
        // Eskiden findBySlugAndActiveTrue kullanılıyordu: pasif salonun slug'ı
        // uygulama kontrolünü geçip DB unique kısıtında 500 ile patlıyordu.
        assertThatThrownBy(() -> salonProvisioningService.provision(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kullanılıyor");
    }

    @Test
    void provision_usesTrialDaysFromRequest() {
        when(salonRepository.existsBySlug("uzun-deneme")).thenReturn(false);
        when(organizationRepository.save(any())).thenAnswer(inv -> {
            Organization o = inv.getArgument(0);
            o.setId(11L);
            return o;
        });
        when(salonRepository.save(any())).thenAnswer(inv -> {
            Salon s = inv.getArgument(0);
            s.setId(21L);
            return s;
        });
        when(userRepository.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(31L);
            return u;
        });
        when(onboardingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(subscriptionPlanRepository.findByCodeAndActiveTrue("SOLO"))
                .thenReturn(Optional.of(SubscriptionPlan.builder()
                        .id(1L).code("SOLO").name("Solo").priceMonthly(BigDecimal.TEN).build()));
        when(organizationSubscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(serviceTemplateService.seedHairAndSkinMenu(21L)).thenReturn(List.of());

        TenantProvisionRequest request = baseRequest("uzun-deneme");
        request.setTrialDays(90);

        TenantProvisionResponse response = salonProvisioningService.provision(request);

        // Davet sahibine 90 gün: süre koddan gelir, varsayılana düşmez.
        assertThat(response.getTrialEnd()).isAfter(LocalDateTime.now().plusDays(89));
        assertThat(response.getTrialEnd()).isBefore(LocalDateTime.now().plusDays(91));
    }

    private TenantProvisionRequest baseRequest(String slug) {
        TenantProvisionRequest request = new TenantProvisionRequest();
        request.setOrganizationName("Test Org");
        request.setSalonName("Test Salon");
        request.setSalonSlug(slug);
        request.setAdminUsername("admin@test");
        request.setAdminPassword("password123");
        return request;
    }
}
