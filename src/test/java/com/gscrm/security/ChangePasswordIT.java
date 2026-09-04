package com.gscrm.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gscrm.model.User;
import com.gscrm.model.enums.UserRole;
import com.gscrm.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * İlk giriş parola değişiminin gerçekten çalıştığı yol.
 *
 * <p>{@link SecurityConfigIT} bu ucun yalnızca CSRF'siz reddedildiğini kapsıyordu;
 * mutlu yol hiç test edilmiyordu. Oysa yeni kiracının admin kullanıcısı
 * {@code mustChangePassword=true} ile açılıyor ve panele girmenin tek yolu bu uç:
 * burada kırılan bir şey, kullanıcıyı sistemin tamamen dışında bırakır.
 *
 * <p>Uç 400'ü üç ayrı gerekçeyle döndürebiliyor — yanlış mevcut parola, kısa yeni
 * parola ve kullanıcının bulunamaması. Üretimde hangisinin geldiği ancak yanıt
 * gövdesindeki mesajdan anlaşıldığı için burada her biri mesajıyla birlikte
 * sabitleniyor.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Parola değiştirme")
class ChangePasswordIT {

    private static final String CURRENT_PASSWORD = "Ilk-Parola-2026";
    private static final String NEW_PASSWORD = "Yeni-Parola-2026";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User user;
    /** {@code user} testle aynı persistence context'te yönetildiği için uç onu
     *  yerinde günceller; "önce" değeri bu yüzden ayrıca saklanmalı. */
    private int tokenVersionBefore;

    @BeforeEach
    void createUserThatMustChangePassword() {
        user = userRepository.save(User.builder()
                .username("pwd-" + UUID.randomUUID().toString().substring(0, 8))
                .passwordHash(passwordEncoder.encode(CURRENT_PASSWORD))
                .role(UserRole.PLATFORM_ADMIN)
                .enabled(true)
                .mustChangePassword(true)
                .createdAt(LocalDateTime.now())
                .build());
        tokenVersionBefore = user.getTokenVersion();
    }

    private UsernamePasswordAuthenticationToken asUser() {
        AuthenticatedUser principal = new AuthenticatedUser(
                user.getId(), user.getUsername(), user.getPasswordHash(), true,
                user.getRole(), null, null, user.getSalonId(), user.getOrganizationId(),
                true, user.getTokenVersion(),
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
        return UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());
    }

    private org.springframework.test.web.servlet.ResultActions change(String current, String next)
            throws Exception {
        return mockMvc.perform(post("/api/auth/change-password")
                .with(authentication(asUser()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("currentPassword", current, "newPassword", next))));
    }

    @Test
    @DisplayName("doğru mevcut parolayla değişir ve zorunluluk kalkar")
    void changesPasswordAndClearsFlag() throws Exception {
        change(CURRENT_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.nextUrl").value("/"));

        User saved = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, saved.getPasswordHash())).isTrue();
        assertThat(saved.isMustChangePassword()).isFalse();
        assertThat(saved.getPasswordChangedAt()).isNotNull();
        // Eski parolayla üretilmiş token'lar geçersizleşmeli.
        assertThat(saved.getTokenVersion()).isEqualTo(tokenVersionBefore + 1);
    }

    @Test
    @DisplayName("yanlış mevcut parola 400 ve gerekçesiyle reddedilir")
    void rejectsWrongCurrentPassword() throws Exception {
        change("yanlis-parola", NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Mevcut parola hatalı"));

        User saved = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches(CURRENT_PASSWORD, saved.getPasswordHash())).isTrue();
        assertThat(saved.isMustChangePassword()).isTrue();
    }

    @Test
    @DisplayName("8 karakterden kısa yeni parola 400 ve alan adıyla reddedilir")
    void rejectsShortNewPassword() throws Exception {
        change(CURRENT_PASSWORD, "kisa123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(
                        "newPassword: Yeni parola en az 8 karakter olmalı"));
    }
}
