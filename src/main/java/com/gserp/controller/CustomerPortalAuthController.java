package com.gserp.controller;

import com.gserp.dto.response.ApiResponse;
import com.gserp.model.Customer;
import com.gserp.model.User;
import com.gserp.model.enums.UserRole;
import com.gserp.repository.CustomerRepository;
import com.gserp.repository.UserRepository;
import com.gserp.security.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth/customer")
@RequiredArgsConstructor
public class CustomerPortalAuthController {

    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtService jwtService;

    public record RegisterRequest(
            @NotBlank String firstName,
            String lastName,
            @NotBlank @Email String email,
            String phone,
            @NotBlank @Size(min = 6) String password
    ) {}

    public record CustomerLoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {}

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Map<String, String>>> register(@Valid @RequestBody RegisterRequest req) {
        if (customerRepository.existsByEmail(req.email())) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Bu e-posta adresi zaten kayıtlı"));
        }
        if (userRepository.existsByUsername(req.email())) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Bu e-posta adresi zaten kullanımda"));
        }

        LocalDateTime now = LocalDateTime.now();

        Customer savedCustomer = customerRepository.save(Customer.builder()
                .firstName(req.firstName())
                .lastName(req.lastName())
                .email(req.email())
                .phone(req.phone())
                .notes("")
                .createdAt(now)
                .updatedAt(now)
                .build());

        User savedUser = userRepository.save(User.builder()
                .username(req.email())
                .passwordHash(passwordEncoder.encode(req.password()))
                .role(UserRole.CUSTOMER)
                .customerId(savedCustomer.getId())
                .enabled(true)
                .createdAt(now)
                .build());

        UserDetails userDetails = buildUserDetails(savedUser);
        return ResponseEntity.ok(ApiResponse.ok("Kayıt başarılı", Map.of(
                "accessToken", jwtService.generateToken(userDetails),
                "refreshToken", jwtService.generateRefreshToken(userDetails)
        )));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, String>>> login(@Valid @RequestBody CustomerLoginRequest req) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.email(), req.password()));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(401).body(ApiResponse.error("Geçersiz e-posta veya parola"));
        }

        User user = userRepository.findByUsername(req.email())
                .orElse(null);
        if (user == null || user.getRole() != UserRole.CUSTOMER) {
            return ResponseEntity.status(403).body(ApiResponse.error("Bu hesap müşteri portalına erişemez"));
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(req.email());
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "accessToken", jwtService.generateToken(userDetails),
                "refreshToken", jwtService.generateRefreshToken(userDetails)
        )));
    }

    private UserDetails buildUserDetails(User user) {
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPasswordHash(),
                user.isEnabled(),
                true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
        );
    }
}
