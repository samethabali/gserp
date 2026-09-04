package com.gscrm.config;

import com.gscrm.tenant.TenantContext;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.ui.Model;

@ControllerAdvice
public class ShowcaseModelAdvice {

    @ModelAttribute
    public void addShowcase(Model model) {
        model.addAttribute("showcase", TenantContext.isShowcase());
    }

    /**
     * Menüdeki "Booking Sayfası" bağlantısının kanonik adresi: {@code /{slug}}.
     *
     * <p>Bağlantı sabit {@code /booking} idi; o adres yalnızca oturumdaki kiracıyla
     * çözülüyor, dolayısıyla yeni sekmede açılan adres paylaşılamıyor ve oturumsuz
     * açıldığında "işletme belirtilmedi" hatası veriyordu. Slug şablona model
     * üzerinden geçilmeli: Thymeleaf {@code th:href} içinde statik sınıf erişimine
     * ({@code T(...)}) izin vermiyor.
     */
    @ModelAttribute
    public void addBookingPath(Model model) {
        String slug = TenantContext.getSlug();
        model.addAttribute("bookingPath",
                slug == null || slug.isBlank() ? "/booking" : "/" + slug);
    }
}
