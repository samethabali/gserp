package com.gscrm.controller;

import com.gscrm.dto.response.ApiResponse;
import com.gscrm.model.Customer;
import com.gscrm.model.User;
import com.gscrm.model.enums.UserRole;
import com.gscrm.repository.CustomerRepository;
import com.gscrm.repository.UserRepository;
import com.gscrm.security.JwtService;
import com.gscrm.security.AuthEventLogger;
import com.gscrm.security.AuthenticatedUser;
import com.gscrm.tenant.TenantContext;
import com.gscrm.validation.PhoneNumber;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.transaction.annotation.Transactional;
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
    private final AuthEventLogger authEventLogger;

    public record RegisterRequest(
            @NotBlank String firstName,
            String lastName,
            @NotBlank @Email String email,
            @PhoneNumber String phone,
            @NotBlank @Size(min = 8, max = 72) String password
    ) {}

    public record CustomerLoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {}

    @PostMapping("/register")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, String>>> register(HttpServletRequest request, @Valid @RequestBody RegisterRequest req) {
        Long salonId = TenantContext.requireSalonId();
        if (customerRepository.existsBySalonIdAndEmail(salonId, req.email())) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Bu e-posta adresi zaten kayıtlı"));
        }
        if (userRepository.existsBySalonIdAndUsername(salonId, req.email())) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Bu e-posta adresi zaten kullanımda"));
        }

        LocalDateTime now = LocalDateTime.now();

        Customer savedCustomer = customerRepository.save(Customer.builder()
                .salonId(salonId)
                .firstName(req.firstName())
                .lastName(req.lastName())
                .email(req.email())
                .phone(req.phone())
                .notes("")
                .consentAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build());

        User savedUser = userRepository.save(User.builder()
                .salonId(salonId)
                .organizationId(TenantContext.getOrgId())
                .username(req.email())
                .passwordHash(passwordEncoder.encode(req.password()))
                .role(UserRole.CUSTOMER)
                .customerId(savedCustomer.getId())
                .enabled(true)
                .mustChangePassword(false)
                .createdAt(now)
                .build());

        UserDetails userDetails = buildUserDetails(savedUser);
        
        authEventLogger.loginSucceeded(request, (AuthenticatedUser) userDetailsService.loadUserByUsername(req.email()));

        return ResponseEntity.ok(ApiResponse.ok("Kayıt başarılı", Map.of(
                "accessToken", jwtService.generateToken(userDetails),
                "refreshToken", jwtService.generateRefreshToken(userDetails)
        )));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, String>>> login(HttpServletRequest request, @Valid @RequestBody CustomerLoginRequest req) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.email(), req.password()));
        } catch (BadCredentialsException e) {
            authEventLogger.loginFailed(request, req.email(), "Geçersiz e-posta veya parola");
            return ResponseEntity.status(401).body(ApiResponse.error("Geçersiz e-posta veya parola"));
        }

        User user = userRepository.findBySalonIdAndUsername(TenantContext.requireSalonId(), req.email())
                .orElse(null);
        if (user == null || user.getRole() != UserRole.CUSTOMER) {
            authEventLogger.loginFailed(request, req.email(), "Bu hesap müşteri portalına erişemez");
            return ResponseEntity.status(403).body(ApiResponse.error("Bu hesap müşteri portalına erişemez"));
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(req.email());
        authEventLogger.loginSucceeded(request, (AuthenticatedUser) userDetails);
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
