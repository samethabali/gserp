package com.gscrm.config;

import com.gscrm.controller.PageController;
import com.gscrm.repository.OnboardingStateRepository;
import com.gscrm.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Menüdeki "Kurulumu tamamla" bağlantısının görünürlüğü.
 *
 * <p>Sihirbaz yalnızca girişten hemen sonra açılıyordu: kullanıcı ondan ayrıldığında
 * (ör. hizmet eklemek için) uygulamanın hiçbir yerinde geri dönecek bağlantı yoktu,
 * kurulum da yarıda kalıyordu. Bağlantı bu yüzden kurulum bitene kadar menüde durur.
 *
 * <p>Tavsiye bilerek {@link PageController} ile sınırlı: sınırsız bir
 * {@code @ControllerAdvice} her REST çağrısında da çalışıp gereksiz bir sorgu açardı.
 */
@ControllerAdvice(assignableTypes = PageController.class)
@RequiredArgsConstructor
public class OnboardingModelAdvice {

    private final OnboardingStateRepository onboardingStateRepository;

    @ModelAttribute
    public void addOnboardingState(Model model) {
        Long salonId = TenantContext.getSalonId();
        boolean incomplete = salonId != null && onboardingStateRepository.findBySalonId(salonId)
                .map(state -> !"COMPLETED".equals(state.getCurrentStep()))
                .orElse(false);
        model.addAttribute("onboardingIncomplete", incomplete);
    }
}
