package com.gscrm.security;

import com.gscrm.model.enums.UserRole;
import com.gscrm.repository.OnboardingStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OnboardingRedirectService {

    /** Platform yöneticisinin ana ekranı; kiracı bağlamı gerektirmeyen tek panel. */
    private static final String PLATFORM_HOME = "/platform/tenants";

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
        // Platform yöneticisi hiçbir salona bağlı olmayabilir (V34 salon_id'yi bu rol
        // için gevşetti). Onu "/" adresine göndermek girişi sonuçsuz bırakıyordu:
        // TenantFilter kiracıyı çözemeyip /login'e geri atıyor, kullanıcı da hiçbir
        // hata görmeden giriş ekranına dönüyordu. Panelin kendisi zaten kiracısız
        // çalışan tek yer, hedef doğrudan orası olmalı.
        if (user.getRole() == UserRole.PLATFORM_ADMIN) {
            return PLATFORM_HOME;
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
