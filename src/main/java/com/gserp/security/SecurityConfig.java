package com.gserp.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] MGMT = {"ADMIN", "BRANCH_MANAGER", "ORG_OWNER", "PLATFORM_ADMIN"};
    private static final String[] MGMT_RECEPTIONIST = {"ADMIN", "BRANCH_MANAGER", "ORG_OWNER", "PLATFORM_ADMIN", "RECEPTIONIST"};
    private static final String[] STAFF_READ = {"ADMIN", "BRANCH_MANAGER", "ORG_OWNER", "PLATFORM_ADMIN", "RECEPTIONIST", "SPECIALIST"};

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final BookingRateLimitFilter bookingRateLimitFilter;
    private final MustChangePasswordSuccessHandler mustChangePasswordSuccessHandler;
    private final UserDetailsService userDetailsService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/**")
            .csrf(csrf -> csrf
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .ignoringRequestMatchers(
                            new AntPathRequestMatcher("/api/auth/**"),
                            new AntPathRequestMatcher("/api/booking/**"),
                            new AntPathRequestMatcher("/api/customer/**"),
                            new AntPathRequestMatcher("/api/onboarding/register"),
                            new AntPathRequestMatcher("/api/webhooks/**")))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/auth/**").permitAll()
                    .requestMatchers("/api/booking/**").permitAll()
                    .requestMatchers("/api/onboarding/register").permitAll()
                    .requestMatchers("/api/webhooks/**").permitAll()
                    .requestMatchers("/api/settings/public").permitAll()
                    .requestMatchers("/api/platform/**").hasRole("PLATFORM_ADMIN")
                    .requestMatchers("/api/org/**").hasAnyRole("ORG_OWNER", "PLATFORM_ADMIN")
                    .requestMatchers("/api/billing/**").authenticated()
                    .requestMatchers("/api/onboarding/**").authenticated()
                    .requestMatchers("/api/settings/**").hasAnyRole(MGMT)
                    .requestMatchers("/api/customer/**").hasRole("CUSTOMER")
                    .requestMatchers("/api/audit/**").hasAnyRole(MGMT)
                    .requestMatchers("/api/campaigns/validate", "/api/campaigns/loyalty-info").authenticated()
                    .requestMatchers("/api/campaigns/**").hasAnyRole(MGMT_RECEPTIONIST)
                    .requestMatchers("/api/payments/**").hasAnyRole(MGMT_RECEPTIONIST)
                    .requestMatchers("/api/expenses/**").hasAnyRole(MGMT_RECEPTIONIST)
                    .requestMatchers("/api/products/**").hasAnyRole(MGMT_RECEPTIONIST)
                    .requestMatchers("/api/waitlist/**").hasAnyRole(MGMT_RECEPTIONIST)
                    .requestMatchers("/api/customers/**").hasAnyRole(MGMT_RECEPTIONIST)
                    .requestMatchers("/api/inventory/**").hasAnyRole(MGMT)
                    .requestMatchers("/api/dashboard/**").hasAnyRole(STAFF_READ)
                    .requestMatchers("/api/appointments/**").hasAnyRole(STAFF_READ)
                    .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/staff/**").hasAnyRole(STAFF_READ)
                    .requestMatchers("/api/staff/**").hasAnyRole(MGMT)
                    .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/services/**").hasAnyRole(STAFF_READ)
                    .requestMatchers("/api/services/**").hasAnyRole(MGMT)
                    .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/resources/**").hasAnyRole(STAFF_READ)
                    .requestMatchers("/api/resources/**").hasAnyRole(MGMT)
                    .requestMatchers("/api/users/**").hasAnyRole(MGMT)
                    .anyRequest().authenticated())
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(bookingRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain webFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .ignoringRequestMatchers(new AntPathRequestMatcher("/ws-calendar/**")))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/login", "/booking", "/privacy", "/css/**", "/js/**", "/images/**",
                            "/webjars/**", "/favicon.ico",
                            "/actuator/health", "/actuator/info",
                            "/customer/login", "/customer/register",
                            "/onboarding/**").permitAll()
                    .requestMatchers("/change-password").authenticated()
                    .requestMatchers("/customer/**").hasRole("CUSTOMER")
                    .requestMatchers("/platform/**").hasRole("PLATFORM_ADMIN")
                    .requestMatchers("/org/**").hasAnyRole("ORG_OWNER", "PLATFORM_ADMIN")
                    .requestMatchers("/audit", "/settings", "/users").hasAnyRole(MGMT)
                    .requestMatchers("/campaigns", "/expenses", "/products",
                            "/staff", "/resources", "/services", "/customers").hasAnyRole(MGMT_RECEPTIONIST)
                    .requestMatchers("/ws-calendar/**").authenticated()
                    .anyRequest().authenticated())
            .formLogin(form -> form
                    .loginPage("/login")
                    .loginProcessingUrl("/login")
                    .successHandler(mustChangePasswordSuccessHandler)
                    .failureUrl("/login?error")
                    .permitAll())
            .logout(logout -> logout
                    .logoutSuccessUrl("/login?logout")
                    .permitAll())
            .authenticationProvider(authenticationProvider());
        return http.build();
    }
}
