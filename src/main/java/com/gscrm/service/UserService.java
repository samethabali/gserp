package com.gscrm.service;

import com.gscrm.dto.request.UserCreateRequest;
import com.gscrm.model.User;
import com.gscrm.model.enums.UserRole;
import com.gscrm.repository.UserRepository;
import com.gscrm.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final QuotaEnforcementService quotaEnforcementService;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listStaffUsers() {
        Long salonId = TenantContext.requireSalonId();
        return userRepository.findBySalonId(salonId).stream()
                .filter(u -> u.getRole() != UserRole.CUSTOMER)
                .map(u -> Map.<String, Object>of(
                        "id", u.getId(),
                        "username", u.getUsername(),
                        "role", u.getRole().name(),
                        "staffId", u.getStaffId() != null ? u.getStaffId() : "",
                        "enabled", u.isEnabled(),
                        "mustChangePassword", u.isMustChangePassword()
                ))
                .toList();
    }

    @Transactional
    public User create(UserCreateRequest req) {
        Long salonId = TenantContext.requireSalonId();
        Long orgId = TenantContext.getOrgId();
        if (orgId != null) {
            quotaEnforcementService.assertCanAddUser(orgId);
        }
        if (userRepository.existsBySalonIdAndUsername(salonId, req.getUsername())) {
            throw new IllegalArgumentException("Bu kullanıcı adı zaten kullanılıyor");
        }
        if (req.getRole() == UserRole.CUSTOMER) {
            throw new IllegalArgumentException("Müşteri hesabı portal kaydı ile oluşturulur");
        }
        User user = User.builder()
                .salonId(salonId)
                .organizationId(TenantContext.getOrgId())
                .username(req.getUsername())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .role(req.getRole())
                .staffId(req.getStaffId())
                .enabled(true)
                .mustChangePassword(true)
                .createdAt(LocalDateTime.now())
                .build();
        return userRepository.save(user);
    }

    @Transactional
    public User resetPassword(Long id, String newPassword) {
        Long salonId = TenantContext.requireSalonId();
        User user = userRepository.findByIdAndSalonId(id, salonId)
                .orElseThrow(() -> new IllegalArgumentException("Kullanıcı bulunamadı"));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(true);
        user.setPasswordChangedAt(null);
        return userRepository.save(user);
    }

    @Transactional
    public User setEnabled(Long id, boolean enabled) {
        Long salonId = TenantContext.requireSalonId();
        User user = userRepository.findByIdAndSalonId(id, salonId)
                .orElseThrow(() -> new IllegalArgumentException("Kullanıcı bulunamadı"));
        user.setEnabled(enabled);
        return userRepository.save(user);
    }
}
