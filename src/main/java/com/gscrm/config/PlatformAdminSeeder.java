package com.gscrm.config;

import com.gscrm.model.User;
import com.gscrm.model.enums.UserRole;
import com.gscrm.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * PLATFORM_ADMIN kullanıcısını env'den yaratır veya mevcut kullanıcıyı yükseltir.
 *
 * <p>Bu rol hiçbir migration'da seed edilmiyordu; sonuç olarak {@code /platform/tenants}
 * paneline — davet kodlarının üretildiği ve işletmelerin takip edildiği tek ekran —
 * hiç girilemiyordu. {@link InitialAdminSeeder}'dan farkı: o yalnızca users tablosu
 * boşken çalışır, bu ise var olan kurulumda da rolü açabilmelidir.
 *
 * <p>Parola yalnızca kullanıcı ilk kez yaratılırken yazılır; sonradan env'de kalması
 * mevcut parolayı ezmez (deploy her seferinde parolayı geri almasın diye).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(20)
public class PlatformAdminSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${GSCRM_PLATFORM_ADMIN_USERNAME:}")
    private String username;

    @Value("${GSCRM_PLATFORM_ADMIN_PASSWORD:}")
    private String password;

    @Override
    @Transactional
    public void run(String... args) {
        if (username == null || username.isBlank()) {
            return;
        }
        String name = username.trim();

        User existing = userRepository.findByUsername(name).orElse(null);
        if (existing != null) {
            if (existing.getRole() != UserRole.PLATFORM_ADMIN) {
                existing.setRole(UserRole.PLATFORM_ADMIN);
                existing.setEnabled(true);
                userRepository.save(existing);
                log.info("'{}' kullanıcısı PLATFORM_ADMIN rolüne yükseltildi.", name);
            }
            return;
        }

        if (password == null || password.isBlank()) {
            log.warn("GSCRM_PLATFORM_ADMIN_USERNAME set edilmiş ama parola yok — "
                    + "platform admin oluşturulamadı.");
            return;
        }

        userRepository.save(User.builder()
                .salonId(1L)
                .organizationId(1L)
                .username(name)
                .passwordHash(passwordEncoder.encode(password))
                .role(UserRole.PLATFORM_ADMIN)
                .enabled(true)
                .mustChangePassword(true)
                .createdAt(LocalDateTime.now())
                .build());
        log.info("Platform admin oluşturuldu: '{}'. İlk girişte parolayı değiştirin.", name);
    }
}
