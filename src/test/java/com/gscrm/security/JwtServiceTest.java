package com.gscrm.security;

import com.gscrm.model.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService(
            "ZGV2LW9ubHktc2VjcmV0LWNoYW5nZS1tZS1kZXYtb25seS1zZWNyZXQtY2hhbmdlLW1l",
            60,
            7);

    @Test
    void accessTokenHasTypAccess() {
        UserDetails user = new AuthenticatedUser(
                1L, "admin", "hash", true, UserRole.BRANCH_MANAGER,
                null, null, 1L, 1L, false, 0,
                List.of(new SimpleGrantedAuthority("ROLE_BRANCH_MANAGER")));
        String token = jwtService.generateToken(user);
        assertEquals(JwtService.TYP_ACCESS, jwtService.extractTokenType(token));
        assertEquals(1L, jwtService.extractSalonId(token));
    }

    @Test
    void refreshTokenNotValidAsAccess() {
        UserDetails user = new AuthenticatedUser(
                1L, "admin", "hash", true, UserRole.BRANCH_MANAGER,
                null, null, 1L, 1L, false, 0,
                List.of(new SimpleGrantedAuthority("ROLE_BRANCH_MANAGER")));
        String refresh = jwtService.generateRefreshToken(user);
        assertFalse(jwtService.validateToken(refresh, user));
        assertTrue(jwtService.validateRefreshToken(refresh, user));
    }

    @Test
    void tokenValidWhenSalonMatches() {
        AuthenticatedUser user = new AuthenticatedUser(
                1L, "reception", "hash", true, UserRole.RECEPTIONIST,
                null, null, 7L, 3L, false, 0,
                List.of(new SimpleGrantedAuthority("ROLE_RECEPTIONIST")));
        String token = jwtService.generateToken(user);
        assertTrue(jwtService.validateToken(token, user));
    }

    @Test
    void tokenRejectedWhenSalonMismatch() {
        AuthenticatedUser salon7User = new AuthenticatedUser(
                1L, "reception", "hash", true, UserRole.RECEPTIONIST,
                null, null, 7L, 3L, false, 0,
                List.of(new SimpleGrantedAuthority("ROLE_RECEPTIONIST")));
        String tokenForSalon7 = jwtService.generateToken(salon7User);

        // Aynı kullanıcı adı, farklı salon bağlamında yüklenmiş (salonId=9).
        AuthenticatedUser salon9User = new AuthenticatedUser(
                1L, "reception", "hash", true, UserRole.RECEPTIONIST,
                null, null, 9L, 4L, false, 0,
                List.of(new SimpleGrantedAuthority("ROLE_RECEPTIONIST")));

        assertFalse(jwtService.validateToken(tokenForSalon7, salon9User));
    }
}
