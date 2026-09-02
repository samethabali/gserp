package com.gscrm.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class MustChangePasswordSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final OnboardingRedirectService onboardingRedirectService;

    public MustChangePasswordSuccessHandler(OnboardingRedirectService onboardingRedirectService) {
        this.onboardingRedirectService = onboardingRedirectService;
        setDefaultTargetUrl("/");
        setAlwaysUseDefaultTargetUrl(false);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, jakarta.servlet.ServletException {
        if (authentication.getPrincipal() instanceof AuthenticatedUser user) {
            String target = onboardingRedirectService.determinePostLoginUrl(user);
            getRedirectStrategy().sendRedirect(request, response, target);
            return;
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
