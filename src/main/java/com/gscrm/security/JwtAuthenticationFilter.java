package com.gscrm.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import com.gscrm.model.enums.UserRole;
import com.gscrm.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsChecker;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    /**
     * Hesabın devre dışı / kilitli / süresi dolmuş olup olmadığını doğrular.
     *
     * <p>Form girişinde bu kontrolü {@code DaoAuthenticationProvider} yapar; JWT
     * yolunda kimlik elle kurulduğu için burada açıkça çağrılmalıdır. Aksi halde
     * devre dışı bırakılan bir kullanıcı, elindeki token'la çalışmaya devam eder.
     */
    private final UserDetailsChecker accountStatusChecker = new AccountStatusUserDetailsChecker();

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(PREFIX.length());
        String username;
        try {
            username = jwtService.extractUsername(token);
        } catch (Exception e) {
            chain.doFilter(request, response);
            return;
        }

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails;
            try {
                userDetails = userDetailsService.loadUserByUsername(username);
                accountStatusChecker.check(userDetails);
            } catch (AuthenticationException e) {
                // Kullanıcı yok, devre dışı veya kilitli — kimlik kurulmadan devam edilir,
                // istek korumalı bir uca gidiyorsa 401 ile sonuçlanır.
                chain.doFilter(request, response);
                return;
            }
            if (jwtService.validateToken(token, userDetails)) {
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
                rememberTenant(request, userDetails);
            }
        }

        chain.doFilter(request, response);
    }

    /**
     * Token'la gelen isteğin salonunu oturuma yazar.
     *
     * <p>Müşteri portalı JWT ile giriş yapıp ardından sunucu tarafında çizilen
     * sayfalara geçiyor; o sayfalarda Authorization başlığı olmadığı için kiracının
     * oturumda hatırlanması gerekiyor. Yalnızca zaten açılmış bir oturum varsa yazılır,
     * yani salt-API çağrıları boşuna oturum açmaz.
     */
    private void rememberTenant(HttpServletRequest request, UserDetails userDetails) {
        if (!(userDetails instanceof AuthenticatedUser user)) {
            return;
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }
        if (user.getSalonId() != null) {
            session.setAttribute(TenantContext.SESSION_AUTH_SALON_ID, user.getSalonId());
        }
        if (user.getRole() == UserRole.PLATFORM_ADMIN) {
            session.setAttribute(TenantContext.SESSION_PLATFORM_ADMIN, Boolean.TRUE);
        }
    }
}
