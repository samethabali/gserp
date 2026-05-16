package com.gserp.security;

import com.gserp.dto.response.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtService jwtService;

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record RefreshRequest(@NotBlank String refreshToken) {}

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, String>>> login(@RequestBody LoginRequest req) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.username(), req.password()));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(401).body(ApiResponse.error("Geçersiz kullanıcı adı veya parola"));
        }
        UserDetails user = userDetailsService.loadUserByUsername(req.username());
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "accessToken", jwtService.generateToken(user),
                "refreshToken", jwtService.generateRefreshToken(user)
        )));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Map<String, String>>> refresh(@RequestBody RefreshRequest req) {
        try {
            String username = jwtService.extractUsername(req.refreshToken());
            UserDetails user = userDetailsService.loadUserByUsername(username);
            if (!jwtService.validateToken(req.refreshToken(), user)) {
                return ResponseEntity.status(401).body(ApiResponse.error("Geçersiz refresh token"));
            }
            return ResponseEntity.ok(ApiResponse.ok(Map.of(
                    "accessToken", jwtService.generateToken(user)
            )));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(ApiResponse.error("Geçersiz refresh token"));
        }
    }
}
