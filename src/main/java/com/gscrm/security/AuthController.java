package com.gscrm.security;

import com.gscrm.dto.response.ApiResponse;
import com.gscrm.model.User;
import com.gscrm.repository.UserRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsChecker;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /** Devre dışı/kilitli hesapların token yenilemesini engeller. */
    private final UserDetailsChecker accountStatusChecker = new AccountStatusUserDetailsChecker();

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record RefreshRequest(@NotBlank String refreshToken) {}
    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8) String newPassword) {}

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
            // Hesap bu arada devre dışı bırakılmış olabilir; yenileme onu diriltmemeli.
            accountStatusChecker.check(user);
            if (!jwtService.validateRefreshToken(req.refreshToken(), user)) {
                return ResponseEntity.status(401).body(ApiResponse.error("Geçersiz refresh token"));
            }
            return ResponseEntity.ok(ApiResponse.ok(Map.of(
                    "accessToken", jwtService.generateToken(user)
            )));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(ApiResponse.error("Geçersiz refresh token"));
        }
    }

    @PostMapping("/change-password")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody ChangePasswordRequest req) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalStateException("Kullanıcı bulunamadı"));
        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Mevcut parola hatalı"));
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        user.setMustChangePassword(false);
        user.setPasswordChangedAt(LocalDateTime.now());
        // Eski parolayla üretilmiş tüm token'ları geçersiz kıl.
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
        return ResponseEntity.ok(ApiResponse.ok("Parola güncellendi", null));
    }
}
