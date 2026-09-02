package com.gscrm.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class MustChangePasswordSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    public MustChangePasswordSuccessHandler() {
        setDefaultTargetUrl("/");
        setAlwaysUseDefaultTargetUrl(false);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, jakarta.servlet.ServletException {
        if (authentication.getPrincipal() instanceof AuthenticatedUser user && user.isMustChangePassword()) {
            getRedirectStrategy().sendRedirect(request, response, "/change-password");
            return;
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
