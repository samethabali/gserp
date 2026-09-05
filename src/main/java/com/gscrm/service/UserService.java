package com.gscrm.service;

import com.gscrm.dto.request.UserCreateRequest;
import com.gscrm.dto.response.UserAccountResponse;
import com.gscrm.model.Staff;
import com.gscrm.model.User;
import com.gscrm.model.enums.UserRole;
import com.gscrm.repository.StaffRepository;
import com.gscrm.repository.UserRepository;
import com.gscrm.security.AuthenticatedUser;
import com.gscrm.security.StaffScopeService;
import com.gscrm.tenant.TenantContext;
import com.gscrm.util.FieldDiff;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    /**
     * Kullanıcı adı forma elle yazılıyor. Büyük harf ve Türkçe karakterlere izin
     * vermek, oluşturulan hesabın giriş ekranında bulunamamasına yol açıyordu.
     */
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9._-]{2,63}$");

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 72;

    /** Formun rol listesi sunucudaki kuralla aynı kalsın diye tek yerde tutulur. */
    private static final List<UserRole> ASSIGNABLE_ROLES = List.of(
            UserRole.ORG_OWNER, UserRole.BRANCH_MANAGER, UserRole.RECEPTIONIST, UserRole.SPECIALIST);

    private final UserRepository userRepository;
    private final StaffRepository staffRepository;
    private final ActivityEventService activityEventService;
    private final PasswordEncoder passwordEncoder;
    private final QuotaEnforcementService quotaEnforcementService;
    private final StaffScopeService staffScopeService;

    @Transactional(readOnly = true)
    public List<UserAccountResponse> listStaffUsers() {
        Long salonId = TenantContext.requireSalonId();
        Long currentUserId = currentUser().getId();
        Map<Long, String> staffNames = new HashMap<>();
        staffRepository.findBySalonId(salonId).forEach(s -> staffNames.put(s.getId(), s.getName()));
        return userRepository.findBySalonId(salonId).stream()
                .filter(u -> u.getRole() != UserRole.CUSTOMER)
                .map(u -> toResponse(u, staffNames.get(u.getStaffId()), currentUserId, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> assignableRoles() {
        UserRole actorRole = currentUser().getRole();
        return ASSIGNABLE_ROLES.stream()
                .filter(r -> rank(r) <= rank(actorRole))
                .map(UserRole::name)
                .toList();
    }

    @Transactional
    public UserAccountResponse create(UserCreateRequest req) {
        Long salonId = TenantContext.requireSalonId();
        AuthenticatedUser actor = currentUser();
        Long orgId = TenantContext.getOrgId();
        if (orgId != null) {
            quotaEnforcementService.assertCanAddUser(orgId);
        }

        String username = normalizeUsername(req.getUsername());
        assertCanAssign(actor.getRole(), req.getRole());

        // Personel kullanıcı adları sistem genelinde tekil (uk_users_username_staff).
        // Yalnızca salon içinde bakmak, başka salonla çakışmayı veritabanı hatasına bırakırdı.
        if (userRepository.countStaffByUsername(username) > 0
                || userRepository.existsBySalonIdAndUsername(salonId, username)) {
            throw new IllegalArgumentException("Bu kullanıcı adı zaten kullanılıyor: " + username);
        }

        Staff staff = resolveStaff(req.getStaffId(), req.getRole(), salonId);
        String password = isBlank(req.getPassword())
                ? StaffAccountService.generateTemporaryPassword()
                : validatedPassword(req.getPassword());

        User user = userRepository.save(User.builder()
                .salonId(salonId)
                .organizationId(orgId)
                .username(username)
                .passwordHash(passwordEncoder.encode(password))
                .role(req.getRole())
                .staffId(staff != null ? staff.getId() : null)
                .enabled(true)
                .mustChangePassword(true)
                .createdAt(LocalDateTime.now())
                .build());

        // Parolanın kendisi hiçbir koşulda kütüğe girmez; yalnızca hesabın açıldığı bilgisi.
        activityEventService.record("CREATE", "USER", user.getId(), null,
                "Kullanıcı oluşturuldu: " + user.getUsername() + " (" + user.getRole() + ")");

        return toResponse(user, staff != null ? staff.getName() : null, actor.getId(), password);
    }

    @Transactional
    public UserAccountResponse resetPassword(Long id, String newPassword) {
        AuthenticatedUser actor = currentUser();
        User user = requireManageableUser(id, actor);

        String password = isBlank(newPassword)
                ? StaffAccountService.generateTemporaryPassword()
                : validatedPassword(newPassword);

        user.setPasswordHash(passwordEncoder.encode(password));
        user.setMustChangePassword(true);
        user.setPasswordChangedAt(null);
        // Yönetici parolayı sıfırladıysa, eski parolayla açılmış oturumlar kapanmalı.
        user.setTokenVersion(user.getTokenVersion() + 1);
        User saved = userRepository.save(user);
        // Parolanın kendisi hiçbir koşulda kütüğe girmez; yalnızca sıfırlandığı bilgisi.
        activityEventService.record("PASSWORD_RESET", "USER", saved.getId(), null,
                "Parola sıfırlandı: " + saved.getUsername());
        return toResponse(saved, staffName(saved), actor.getId(), password);
    }

    @Transactional
    public UserAccountResponse setEnabled(Long id, boolean enabled) {
        AuthenticatedUser actor = currentUser();
        User user = requireManageableUser(id, actor);
        if (!enabled && user.getId().equals(actor.getId())) {
            throw new IllegalArgumentException("Kendi hesabınızı devre dışı bırakamazsınız");
        }
        boolean prevEnabled = user.isEnabled();
        user.setEnabled(enabled);
        if (!enabled) {
            // Devre dışı bırakma, dağıtılmış token'ları da anında geçersizleştirmeli;
            // aksi halde kullanıcı yenileme token'ıyla günlerce erişmeye devam eder.
            user.setTokenVersion(user.getTokenVersion() + 1);
        }
        User saved = userRepository.save(user);
        activityEventService.recordChange(enabled ? "ACTIVATE" : "DEACTIVATE", "USER", saved.getId(), null,
                (enabled ? "Kullanıcı aktifleştirildi: " : "Kullanıcı devre dışı bırakıldı: ") + saved.getUsername(),
                FieldDiff.create().compare("aktif", prevEnabled, saved.isEnabled()).toJson());
        return toResponse(saved, staffName(saved), actor.getId(), null);
    }

    // --------------------------------- Yardımcılar ---------------------------------

    private AuthenticatedUser currentUser() {
        return staffScopeService.requireAuthenticatedUser();
    }

    /**
     * Rol hiyerarşisi. Atamalar bununla sınırlanmazsa salon yöneticisi kendine
     * PLATFORM_ADMIN hesabı açıp tüm kiracılara erişebilirdi.
     */
    private static int rank(UserRole role) {
        return switch (role) {
            case PLATFORM_ADMIN -> 4;
            case ORG_OWNER -> 3;
            case BRANCH_MANAGER, ADMIN -> 2;
            case RECEPTIONIST, SPECIALIST -> 1;
            case CUSTOMER -> 0;
        };
    }

    private static void assertCanAssign(UserRole actorRole, UserRole target) {
        if (target == UserRole.CUSTOMER) {
            throw new IllegalArgumentException("Müşteri hesabı portal kaydı ile oluşturulur");
        }
        if (target == UserRole.PLATFORM_ADMIN) {
            throw new IllegalArgumentException("Platform yöneticisi bu ekrandan oluşturulamaz");
        }
        if (rank(target) > rank(actorRole)) {
            throw new IllegalArgumentException("Kendi yetkinizin üzerinde bir rol atayamazsınız");
        }
    }

    /** Hedef hem aynı salonda hem de işlemi yapanın yetkisini aşmayan bir rolde olmalı. */
    private User requireManageableUser(Long id, AuthenticatedUser actor) {
        User user = userRepository.findByIdAndSalonId(id, TenantContext.requireSalonId())
                .orElseThrow(() -> new IllegalArgumentException("Kullanıcı bulunamadı"));
        if (rank(user.getRole()) > rank(actor.getRole())) {
            throw new IllegalArgumentException("Bu kullanıcıyı yönetme yetkiniz yok");
        }
        return user;
    }

    private static String normalizeUsername(String raw) {
        String username = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new IllegalArgumentException(
                    "Kullanıcı adı 3-64 karakter olmalı; yalnızca küçük harf, rakam, nokta, "
                            + "tire ve alt çizgi içerebilir");
        }
        return username;
    }

    private static String validatedPassword(String password) {
        if (password.length() < MIN_PASSWORD_LENGTH || password.length() > MAX_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "Parola " + MIN_PASSWORD_LENGTH + "-" + MAX_PASSWORD_LENGTH + " karakter arasında olmalı");
        }
        return password;
    }

    /**
     * Uzman hesabı personel kaydına bağlı olmalı: staffId boş kalırsa kullanıcı giriş
     * yapar ama randevu uçlarının hepsinden "personel kaydına bağlı değil" hatası alır.
     */
    private Staff resolveStaff(Long staffId, UserRole role, Long salonId) {
        if (staffId == null) {
            if (role == UserRole.SPECIALIST) {
                throw new IllegalArgumentException("Uzman hesabı bir personel kaydına bağlanmalı");
            }
            return null;
        }
        Staff staff = staffRepository.findByIdAndSalonId(staffId, salonId)
                .orElseThrow(() -> new IllegalArgumentException("Personel bulunamadı"));
        if (userRepository.findBySalonIdAndStaffId(salonId, staff.getId()).isPresent()) {
            throw new IllegalArgumentException("Bu personelin zaten bir giriş hesabı var: " + staff.getName());
        }
        return staff;
    }

    private String staffName(User user) {
        if (user.getStaffId() == null) {
            return null;
        }
        return staffRepository.findByIdAndSalonId(user.getStaffId(), user.getSalonId())
                .map(Staff::getName)
                .orElse(null);
    }

    private static UserAccountResponse toResponse(User user, String staffName,
                                                  Long currentUserId, String temporaryPassword) {
        return new UserAccountResponse(user.getId(), user.getUsername(), user.getRole().name(),
                user.getStaffId(), staffName, user.isEnabled(), user.isMustChangePassword(),
                user.getId().equals(currentUserId), temporaryPassword);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
