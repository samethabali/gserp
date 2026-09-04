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
        return determineSetupUrl(user);
    }

    /**
     * Parola değişimi sonrası hedef.
     *
     * <p>Yeni kiracının admin kullanıcısı {@code mustChangePassword=true} ile
     * açılıyor, dolayısıyla ilk giriş her zaman {@code /change-password}'a düşüyor
     * ve oradan doğrudan ana sayfaya gidiliyordu: kurulum sihirbazı ilk oturumda
     * hiç görünmüyordu. Parola değişince onboarding kontrolü burada yapılır —
     * {@code mustChangePassword} dalına takılmadan.
     */
    public String determineSetupUrl(AuthenticatedUser user) {
        if (user.getSalonId() == null) {
            return "/";
        }
        return onboardingStateRepository.findBySalonId(user.getSalonId())
                .filter(state -> !"COMPLETED".equals(state.getCurrentStep()))
                .map(state -> "/onboarding/setup")
                .orElse("/");
    }
}
