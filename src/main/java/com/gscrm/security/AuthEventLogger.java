package com.gscrm.security;

import com.gscrm.service.ActivityEventService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Kimlik doğrulama olaylarını işlem kütüğüne yazar.
 *
 * <p>Giriş, çıkış ve başarısız denemeler hiçbir yerde kayıt altına alınmıyordu:
 * {@code ActivityAuditFilter} {@code /api/auth/**} yolunu atlıyor, form login ise
 * {@code /api/} altında bile değil. "Kim ne zaman girdi", "kaç kez yanlış parola
 * denendi" sorularının cevabı yoktu.
 */
@Component
@RequiredArgsConstructor
public class AuthEventLogger {

    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    public static final String LOGOUT = "LOGOUT";
    public static final String PASSWORD_CHANGE = "PASSWORD_CHANGE";
    public static final String TOKEN_REFRESH = "TOKEN_REFRESH";

    private final ActivityEventService activityEventService;
    private final ClientIpResolver clientIpResolver;

    public void loginSucceeded(HttpServletRequest request, AuthenticatedUser user) {
        activityEventService.recordAuth(LOGIN_SUCCESS, user.getUsername(), user.getId(), user.getSalonId(),
                ActivityEventService.OUTCOME_SUCCESS,
                "Giriş yapıldı (" + user.getRole().name() + ")",
                clientIpResolver.resolve(request));
    }

    public void loginFailed(HttpServletRequest request, String username, String reason) {
        activityEventService.recordAuth(LOGIN_FAILED, username, null, null,
                ActivityEventService.OUTCOME_DENIED,
                "Başarısız giriş denemesi: " + reason,
                clientIpResolver.resolve(request));
    }

    public void loggedOut(HttpServletRequest request, AuthenticatedUser user) {
        activityEventService.recordAuth(LOGOUT, user.getUsername(), user.getId(), user.getSalonId(),
                ActivityEventService.OUTCOME_SUCCESS, "Oturum kapatıldı",
                clientIpResolver.resolve(request));
    }

    public void passwordChanged(HttpServletRequest request, AuthenticatedUser user) {
        activityEventService.recordAuth(PASSWORD_CHANGE, user.getUsername(), user.getId(), user.getSalonId(),
                ActivityEventService.OUTCOME_SUCCESS, "Parola değiştirildi",
                clientIpResolver.resolve(request));
    }

    public void tokenRefreshed(HttpServletRequest request, AuthenticatedUser user) {
        activityEventService.recordAuth(TOKEN_REFRESH, user.getUsername(), user.getId(), user.getSalonId(),
                ActivityEventService.OUTCOME_SUCCESS, "Erişim anahtarı yenilendi",
                clientIpResolver.resolve(request));
    }
}
