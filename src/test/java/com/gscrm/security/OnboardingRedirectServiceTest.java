package com.gscrm.security;

import com.gscrm.model.OnboardingState;
import com.gscrm.model.enums.UserRole;
import com.gscrm.repository.OnboardingStateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OnboardingRedirectServiceTest {

    @Mock
    private OnboardingStateRepository onboardingStateRepository;

    @InjectMocks
    private OnboardingRedirectService onboardingRedirectService;

    @Test
    void redirectsToChangePasswordWhenRequired() {
        AuthenticatedUser user = user(1L, true);
        assertThat(onboardingRedirectService.determinePostLoginUrl(user)).isEqualTo("/change-password");
    }

    @Test
    void redirectsToSetupWhenOnboardingIncomplete() {
        AuthenticatedUser user = user(1L, false);
        when(onboardingStateRepository.findBySalonId(1L)).thenReturn(Optional.of(
                OnboardingState.builder().salonId(1L).currentStep("SERVICES").build()));

        assertThat(onboardingRedirectService.determinePostLoginUrl(user)).isEqualTo("/onboarding/setup");
    }

    @Test
    void redirectsToCalendarWhenOnboardingCompleted() {
        AuthenticatedUser user = user(1L, false);
        when(onboardingStateRepository.findBySalonId(1L)).thenReturn(Optional.of(
                OnboardingState.builder().salonId(1L).currentStep("COMPLETED").build()));

        assertThat(onboardingRedirectService.determinePostLoginUrl(user)).isEqualTo("/");
    }

    private AuthenticatedUser user(Long salonId, boolean mustChangePassword) {
        return new AuthenticatedUser(
                1L, "demo", "hash", true, UserRole.BRANCH_MANAGER,
                null, null, salonId, 1L, mustChangePassword, 0,
                List.of(new SimpleGrantedAuthority("ROLE_BRANCH_MANAGER")));
    }
}
