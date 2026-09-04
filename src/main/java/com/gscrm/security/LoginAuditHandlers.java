package com.gscrm.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
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

        @Override
        public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response,
                                    Authentication authentication)
                throws IOException, jakarta.servlet.ServletException {
            if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
                authEventLogger.loggedOut(request, user);
            }
            setDefaultTargetUrl("/login?logout");
            super.onLogoutSuccess(request, response, authentication);
        }
    }
}
