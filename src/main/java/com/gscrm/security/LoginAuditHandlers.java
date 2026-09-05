package com.gscrm.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Form login'in başarısız giriş ve çıkış olaylarını kütüğe yazan handler'ları.
 *
 * <p>Spring'in varsayılan handler'ları yalnızca yönlendirme yapıyordu; başarısız
 * deneme hiçbir yerde iz bırakmıyordu.
 */
public final class LoginAuditHandlers {

    private LoginAuditHandlers() {
    }

    @Component
    @RequiredArgsConstructor
    public static class AuditingFailureHandler extends SimpleUrlAuthenticationFailureHandler {

        private final AuthEventLogger authEventLogger;

        @Override
        public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                            AuthenticationException exception)
                throws IOException, jakarta.servlet.ServletException {
            String username = request.getParameter("username");
            authEventLogger.loginFailed(request, username, exception.getClass().getSimpleName());
            setDefaultFailureUrl("/login?error");
            super.onAuthenticationFailure(request, response, exception);
        }
    }

    @Component
    @RequiredArgsConstructor
    public static class AuditingLogoutSuccessHandler extends SimpleUrlLogoutSuccessHandler {

        private final AuthEventLogger authEventLogger;
        private final ImpersonationService impersonationService;

        @Override
        public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response,
                                    Authentication authentication)
                throws IOException, jakarta.servlet.ServletException {
            if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
                authEventLogger.loggedOut(request, user);
            }
            // Platform admin başka bir hesaba girdiyse, çıkış o kaydı da kapatmalı.
            var session = request.getSession(false);
            if (session != null) {
                Object impersonatorId = session.getAttribute(ImpersonationService.SESSION_IMPERSONATOR_ID);
                if (impersonatorId instanceof Long id) {
                    impersonationService.endImpersonation(id);
                }
            }
            setDefaultTargetUrl("/login?logout");
            super.onLogoutSuccess(request, response, authentication);
        }
    }

    /**
     * Yetkisiz erişim denemelerini kütüğe yazar.
     *
     * <p>Reddedilen istekler hiçbir iz bırakmıyordu: bir resepsiyonistin yönetici
     * ucunu denemesi ya da bir kiracının başkasının verisine uzanması yalnızca 403
     * dönüyor, kimse görmüyordu. Kayıt {@code outcome=DENIED} ile yazılır.
     */
    @Component
    @RequiredArgsConstructor
    public static class AuditingAccessDeniedHandler implements AccessDeniedHandler {

        private final com.gscrm.service.ActivityEventService activityEventService;
        private final ClientIpResolver clientIpResolver;
        private final AccessDeniedHandlerImpl delegate = new AccessDeniedHandlerImpl();

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response,
                           AccessDeniedException exception)
                throws IOException, jakarta.servlet.ServletException {
            activityEventService.recordHttp(request.getMethod(), null,
                    "Yetkisiz erişim denemesi: " + request.getMethod() + " " + request.getRequestURI(),
                    HttpServletResponse.SC_FORBIDDEN,
                    clientIpResolver.resolve(request));
            delegate.handle(request, response, exception);
        }
    }
}
