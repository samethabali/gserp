package com.gscrm.security;

import com.gscrm.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Girişten sonra kiracıyı oturuma yazar, olayı kütüğe geçirir ve kullanıcıyı
 * doğru sayfaya yönlendirir.
 *
 * <p>Kiracının burada yazılması, alt alan adı çözümlemesinin yerini alan mekanizmanın
 * kendisidir: oturum boyunca isteğin hangi salona ait olduğunu adres değil, giriş
 * yapan kullanıcının kaydı belirler.
 */
@Component
public class MustChangePasswordSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final OnboardingRedirectService onboardingRedirectService;
    private final AuthEventLogger authEventLogger;

    public MustChangePasswordSuccessHandler(OnboardingRedirectService onboardingRedirectService,
                                            AuthEventLogger authEventLogger) {
        this.onboardingRedirectService = onboardingRedirectService;
        this.authEventLogger = authEventLogger;
        setDefaultTargetUrl("/");
        setAlwaysUseDefaultTargetUrl(false);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, jakarta.servlet.ServletException {
        if (authentication.getPrincipal() instanceof AuthenticatedUser user) {
            if (user.getSalonId() != null) {
                request.getSession(true).setAttribute(TenantContext.SESSION_AUTH_SALON_ID, user.getSalonId());
            }
            authEventLogger.loginSucceeded(request, user);
            String target = onboardingRedirectService.determinePostLoginUrl(user);
            getRedirectStrategy().sendRedirect(request, response, target);
            return;
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
