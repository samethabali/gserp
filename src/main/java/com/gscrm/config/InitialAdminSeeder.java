package com.gscrm.config;

import com.gscrm.model.User;
import com.gscrm.model.enums.UserRole;
import com.gscrm.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * users tablosu boşsa, GSCRM_INITIAL_ADMIN_USERNAME/PASSWORD env'lerinden
 * tek seferlik admin yaratır. Tüm profillerde aktif — prod'da ilk kurulum,
 * dev'de DevDataSeeder ile çakışmaz (DevDataSeeder bunu çağırmıyor, ikisi de
 * "users boşsa" kontrolüyle idempotent).
 *
 * Env değerleri boş bırakılırsa hiçbir şey yapılmaz (ör. admin'in manuel SQL
 * ile yaratılacağı kurulumlar).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InitialAdminSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${GSCRM_INITIAL_ADMIN_USERNAME:}")
    private String initialUsername;

    @Value("${GSCRM_INITIAL_ADMIN_PASSWORD:}")
    private String initialPassword;

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return;
        }
        if (initialUsername == null || initialUsername.isBlank()
                || initialPassword == null || initialPassword.isBlank()) {
            log.warn("users tablosu boş ama GSCRM_INITIAL_ADMIN_USERNAME/PASSWORD set edilmemiş — "
                    + "admin manuel oluşturulmalı");
            return;
        }
        User admin = User.builder()
                .salonId(1L)
                .username(initialUsername)
                .passwordHash(passwordEncoder.encode(initialPassword))
                .role(UserRole.ADMIN)
                .enabled(true)
                .mustChangePassword(true)
                .createdAt(LocalDateTime.now())
                .build();
        userRepository.save(admin);
        log.info("İlk admin kullanıcısı oluşturuldu: '{}'. İlk girişte parolayı değiştirin.",
                initialUsername);
    }
}
