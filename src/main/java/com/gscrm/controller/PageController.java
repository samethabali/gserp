package com.gscrm.controller;

import com.gscrm.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/")
    public String calendar() {
        return "index";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/change-password")
    public String changePassword() {
        return "change-password";
    }

    @GetMapping("/privacy")
    public String privacy() {
        return "privacy";
    }

    @GetMapping("/dashboard")
    public String dashboard() {
        return "dashboard";
    }

    @GetMapping("/services")
    public String services() {
        return "services";
    }

    @GetMapping("/staff")
    public String staff() {
        return "staff";
    }

    @GetMapping("/resources")
    public String resources() {
        return "resources";
    }

    @GetMapping("/audit")
    public String audit() {
        return "audit";
    }

    @GetMapping("/customers")
    public String customers() {
        return "customers";
    }

    @GetMapping("/expenses")
    public String expenses() {
        return "expenses";
    }

    @GetMapping("/products")
    public String products() {
        return "products";
    }

    /**
     * İşletmenin herkese açık randevu sayfası.
     *
     * <p>İşletme adresten belirlenir. Daha önce bu bilgi alt alan adından geliyordu;
     * üretimde wildcard DNS ve sertifika olmadığı için o adresler hiç açılmıyor,
     * kayıt olan işletmeye verilen adres de çalışmıyordu.
     *
     * <p>Seçim oturuma yazılır ki müşteri portalı giriş/kayıt sayfaları gibi
     * devamındaki adımlar da aynı işletmede kalsın.
     */
    @GetMapping("/b/{slug}")
    public String legacyPublicBooking(HttpServletRequest request) {
        request.getSession(true).setAttribute(TenantContext.SESSION_PUBLIC_SALON_ID, TenantContext.getSalonId());
        return "redirect:/" + TenantContext.getSlug();
    }

    /** Yeni kanonik randevu adresi: https://gserp.avesitesi.xyz/{isletme-adi}. */
    @GetMapping("/{slug:[a-z0-9][a-z0-9-]*}")
    public String publicBooking(HttpServletRequest request) {
        request.getSession(true).setAttribute(TenantContext.SESSION_PUBLIC_SALON_ID, TenantContext.getSalonId());
        return "booking";
    }

    /**
     * Eski randevu adresi — yalnızca kanonik adrese yönlendirir.
     *
     * <p>Burası randevu sayfasını kendisi çiziyordu ve adres çubuğunda {@code /booking}
     * kalıyordu: KVKK metni veya müşteri girişi gibi bir ara sayfadan dönen ziyaretçi
     * işletmesiz bir adrese düşüyor, o adresi paylaştığında ya da yeniden açtığında
     * "işletme seçilmedi" görüyordu. Kiracı çözülebiliyorsa adres {@code /{slug}}
     * olarak düzeltilir; çözülemiyorsa bilgilendirme sayfası kalır.
     */
    @GetMapping("/booking")
    public String booking() {
        String slug = TenantContext.getSlug();
        if (slug == null || slug.isBlank()) {
            return "booking-no-salon";
        }
        return "redirect:/" + slug;
    }

    @GetMapping("/customer/login")
    public String customerLogin() {
        return "customer-login";
    }

    @GetMapping("/customer/register")
    public String customerRegister() {
        return "customer-register";
    }

    @GetMapping("/customer/portal")
    public String customerPortal() {
        return "customer-portal";
    }

    @GetMapping("/campaigns")
    public String campaigns() {
        return "campaigns";
    }

    @GetMapping("/users")
    public String users() {
        return "users";
    }

    @GetMapping("/settings")
    public String settings() {
        return "settings";
    }

    @GetMapping("/settings/billing")
    public String billing() {
        return "billing";
    }

    @GetMapping("/platform/tenants")
    public String platformTenants() {
        return "platform/tenants";
    }

    @GetMapping("/org/dashboard")
    public String orgDashboard() {
        return "org/dashboard";
    }

    @GetMapping("/onboarding/wizard")
    public String onboardingWizard() {
        return "onboarding/wizard";
    }

    @GetMapping("/onboarding/setup")
    public String onboardingSetup() {
        return "onboarding/setup";
    }
}
