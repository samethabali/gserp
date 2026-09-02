package com.gscrm.security;

import com.gscrm.repository.OnboardingStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OnboardingRedirectService {

    private final OnboardingStateRepository onboardingStateRepository;

    public String determinePostLoginUrl(AuthenticatedUser user) {
        if (user.isMustChangePassword()) {
            return "/change-password";
        }
        if (user.getSalonId() == null) {
            return "/";
        }
        return onboardingStateRepository.findBySalonId(user.getSalonId())
                .filter(state -> !"COMPLETED".equals(state.getCurrentStep()))
                .map(state -> "/onboarding/setup")
                .orElse("/");
    }
}
