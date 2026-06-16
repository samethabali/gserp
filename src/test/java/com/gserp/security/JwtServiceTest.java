package com.gserp.security;

import com.gserp.model.enums.UserRole;
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
                null, null, 1L, 1L, false,
                List.of(new SimpleGrantedAuthority("ROLE_BRANCH_MANAGER")));
        String token = jwtService.generateToken(user);
        assertEquals(JwtService.TYP_ACCESS, jwtService.extractTokenType(token));
        assertEquals(1L, jwtService.extractSalonId(token));
    }

    @Test
    void refreshTokenNotValidAsAccess() {
        UserDetails user = new AuthenticatedUser(
                1L, "admin", "hash", true, UserRole.BRANCH_MANAGER,
                null, null, 1L, 1L, false,
                List.of(new SimpleGrantedAuthority("ROLE_BRANCH_MANAGER")));
        String refresh = jwtService.generateRefreshToken(user);
        assertFalse(jwtService.validateToken(refresh, user));
        assertTrue(jwtService.validateRefreshToken(refresh, user));
    }
}
