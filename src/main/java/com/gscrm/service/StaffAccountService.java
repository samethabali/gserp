package com.gscrm.service;

import com.gscrm.dto.response.StaffAccountResponse;
import com.gscrm.model.Staff;
import com.gscrm.model.User;
import com.gscrm.model.enums.StaffRole;
import com.gscrm.model.enums.UserRole;
import com.gscrm.repository.StaffRepository;
import com.gscrm.repository.UserRepository;
import com.gscrm.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Personel kayıtlarının giriş hesaplarını yönetir.
 *
 * <p>Personel ve kullanıcı ayrı kavramlardı: salon sahibi personeli ekliyor, sonra
 * kullanıcı ekranından elle bir hesap açıp {@code staffId}'yi kendi eşlemek zorunda
 * kalıyordu — pratikte kimse yapmadığı için personel sisteme hiç giremiyordu.
 * Burada personel eklendiği anda hesabı da açılır, geçici parola üretilir ve
 * {@code mustChangePassword} ile ilk girişte parola değişimi zorunlu kılınır.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StaffAccountService {

    /** Karışabilen karakterler (0/O, 1/l/I) geçici paroladan çıkarıldı: parola elden veya sözlü iletiliyor. */
    private static final char[] PASSWORD_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();
    private static final char[] SUFFIX_ALPHABET = "abcdefghijkmnopqrstuvwxyz23456789".toCharArray();

    private static final Map<Character, Character> TURKISH_FOLD = Map.of(
            'ç', 'c', 'ğ', 'g', 'ı', 'i', 'ö', 'o', 'ş', 's', 'ü', 'u');

    private static final Locale TR = Locale.forLanguageTag("tr-TR");
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final int MAX_USERNAME_LENGTH = 64;
    private static final int MAX_BASE_LENGTH = 48;

    private final UserRepository userRepository;
    private final StaffRepository staffRepository;
    private final PasswordEncoder passwordEncoder;
    private final QuotaEnforcementService quotaEnforcementService;
    private final ActivityEventService activityEventService;

    // --------------------------------- Sorgular ---------------------------------

    /** Panelin personel kartlarında "hesabı var mı" bilgisini göstermesi için. */
    public List<StaffAccountResponse> listAccounts() {
        Long salonId = TenantContext.requireSalonId();
        return userRepository.findBySalonIdAndStaffIdNotNull(salonId).stream()
                .map(StaffAccountService::toResponse)
                .toList();
    }

    public Optional<User> findAccount(Long staffId) {
        return userRepository.findBySalonIdAndStaffId(TenantContext.requireSalonId(), staffId);
    }

    /**
     * Hesabın açılmasını engelleyen durum varsa açıklamasını döner, yoksa {@code null}.
     *
     * <p>Kontrol istisna yerine metinle bildirilir: kotayı doğrulayan servis de
     * işlemsel olduğu için ondan sızan bir istisna, personel kaydını yazan dış işlemi
     * rollback-only işaretler ve personelin kendisi de kaydedilemezdi.
     */
    public String provisionBlocker(Staff staff) {
        Long salonId = TenantContext.requireSalonId();
        if (userRepository.findBySalonIdAndStaffId(salonId, staff.getId()).isPresent()) {
            return "Bu personelin zaten bir giriş hesabı var";
        }
        return quotaEnforcementService.userSeatBlocker(TenantContext.getOrgId());
    }

    // --------------------------------- Yazma ---------------------------------

    /**
     * Personele giriş hesabı açar ve tek seferlik geçici parolayı yanıtta döner.
     */
    @Transactional
    public StaffAccountResponse provision(Staff staff) {
        Long salonId = TenantContext.requireSalonId();
        String blocker = provisionBlocker(staff);
        if (blocker != null) {
            throw new IllegalStateException(blocker);
        }

        String username = generateUsername(staff.getName(), salonId);
        String temporaryPassword = generateTemporaryPassword();

        User user = userRepository.save(User.builder()
                .salonId(salonId)
                .organizationId(TenantContext.getOrgId())
                .username(username)
                .passwordHash(passwordEncoder.encode(temporaryPassword))
                .role(toUserRole(staff.getRole()))
                .staffId(staff.getId())
                .enabled(staff.isActive())
                .mustChangePassword(true)
                .createdAt(LocalDateTime.now())
                .build());

        // Parolanın kendisi hiçbir koşulda kütüğe girmez; yalnızca hesabın açıldığı bilgisi.
        activityEventService.record("CREATE", "USER", user.getId(), null,
                "Personel hesabı açıldı: " + staff.getName() + " (" + username + ")");

        return new StaffAccountResponse(staff.getId(), user.getId(), username,
                user.getRole().name(), user.isEnabled(), true, temporaryPassword);
    }

    @Transactional
    public StaffAccountResponse provision(Long staffId) {
        return provision(requireStaff(staffId));
    }

    /**
     * Yeni bir geçici parola üretir — personel parolasını unuttuğunda salon sahibi
     * bunu kullanır. Eski parolayla açılmış oturumlar {@code tokenVersion} artışıyla düşer.
     */
    @Transactional
    public StaffAccountResponse resetPassword(Long staffId) {
        Staff staff = requireStaff(staffId);
        User user = findAccount(staffId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Bu personelin giriş hesabı yok: " + staff.getName()));

        String temporaryPassword = generateTemporaryPassword();
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        user.setMustChangePassword(true);
        user.setPasswordChangedAt(null);
        user.setTokenVersion(user.getTokenVersion() + 1);
        User saved = userRepository.save(user);

        activityEventService.record("PASSWORD_RESET", "USER", saved.getId(), null,
                "Personel parolası sıfırlandı: " + staff.getName() + " (" + saved.getUsername() + ")");

        return new StaffAccountResponse(staffId, saved.getId(), saved.getUsername(),
                saved.getRole().name(), saved.isEnabled(), true, temporaryPassword);
    }

    /**
     * Personel kaydındaki rol ve aktiflik değişimini hesaba yansıtır.
     *
     * <p>Bu olmadan işten ayrılan personelin kaydı pasife alınsa bile hesabı
     * çalışmaya devam ederdi.
     */
    @Transactional
    public void syncWithStaff(Staff staff) {
        Optional<User> maybeUser = userRepository.findBySalonIdAndStaffId(staff.getSalonId(), staff.getId());
        if (maybeUser.isEmpty()) {
            return;
        }
        User user = maybeUser.get();
        UserRole expectedRole = toUserRole(staff.getRole());
        boolean changed = false;

        if (user.getRole() != expectedRole) {
            user.setRole(expectedRole);
            // Rol yetkisi token'a gömülü; eski token yeni rolü bilmez.
            user.setTokenVersion(user.getTokenVersion() + 1);
            changed = true;
        }
        if (user.isEnabled() != staff.isActive()) {
            user.setEnabled(staff.isActive());
            if (!staff.isActive()) {
                user.setTokenVersion(user.getTokenVersion() + 1);
            }
            changed = true;
        }
        if (changed) {
            userRepository.save(user);
            activityEventService.record("UPDATE", "USER", user.getId(), null,
                    "Personel hesabı personel kaydıyla eşitlendi: " + user.getUsername()
                            + " (" + user.getRole() + ", " + (user.isEnabled() ? "aktif" : "pasif") + ")");
        }
    }

    // --------------------------------- Yardımcılar ---------------------------------

    private Staff requireStaff(Long staffId) {
        return staffRepository.findByIdAndSalonId(staffId, TenantContext.requireSalonId())
                .orElseThrow(() -> new IllegalArgumentException("Personel bulunamadı: " + staffId));
    }

    /** ADMIN, {@link UserRole#ADMIN}'in güncel karşılığı olan BRANCH_MANAGER'a düşer. */
    static UserRole toUserRole(StaffRole role) {
        if (role == null) {
            return UserRole.SPECIALIST;
        }
        return switch (role) {
            case ADMIN -> UserRole.BRANCH_MANAGER;
            case RECEPTIONIST -> UserRole.RECEPTIONIST;
            case SPECIALIST -> UserRole.SPECIALIST;
        };
    }

    private static StaffAccountResponse toResponse(User user) {
        return new StaffAccountResponse(user.getStaffId(), user.getId(), user.getUsername(),
                user.getRole().name(), user.isEnabled(), user.isMustChangePassword(), null);
    }

    /**
     * "Ayşe Yılmaz" -> "ayse.yilmaz". Personel kullanıcı adları V33'ten beri sistem
     * genelinde tekil olduğu için çakışma sırayla numaralandırılır.
     */
    String generateUsername(String displayName, Long salonId) {
        String base = slugify(displayName);
        if (base.isEmpty()) {
            base = "personel";
        }
        if (base.length() > MAX_BASE_LENGTH) {
            base = base.substring(0, MAX_BASE_LENGTH);
        }
        for (int i = 1; i <= 200; i++) {
            String candidate = (i == 1) ? base : base + i;
            if (!isUsernameTaken(candidate, salonId)) {
                return candidate;
            }
        }
        for (int i = 0; i < 20; i++) {
            String candidate = base + "." + randomToken(SUFFIX_ALPHABET, 5);
            if (!isUsernameTaken(candidate, salonId)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Kullanıcı adı üretilemedi, lütfen hesabı elle oluşturun");
    }

    /**
     * İki ayrı tekillik kuralı var: personel kullanıcı adları sistem genelinde
     * ({@code uk_users_username_staff}), müşteri hesapları salon bazında
     * ({@code uk_users_salon_username}) tekil. Aday ikisine de takılmamalı.
     */
    private boolean isUsernameTaken(String candidate, Long salonId) {
        if (candidate.length() > MAX_USERNAME_LENGTH) {
            return true;
        }
        return userRepository.countStaffByUsername(candidate) > 0
                || userRepository.existsBySalonIdAndUsername(salonId, candidate);
    }

    static String slugify(String raw) {
        if (raw == null) {
            return "";
        }
        String lower = raw.trim().toLowerCase(TR);
        StringBuilder folded = new StringBuilder(lower.length());
        for (char c : lower.toCharArray()) {
            Character replacement = TURKISH_FOLD.get(c);
            folded.append(replacement != null ? replacement : c);
        }
        String ascii = Normalizer.normalize(folded, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");

        StringBuilder out = new StringBuilder(ascii.length());
        for (char c : ascii.toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                out.append(c);
            } else if (out.length() > 0 && out.charAt(out.length() - 1) != '.') {
                out.append('.');
            }
        }
        String slug = out.toString();
        while (slug.endsWith(".")) {
            slug = slug.substring(0, slug.length() - 1);
        }
        return slug;
    }

    /** 3x4 karakterlik, tireli ve okunaklı geçici parola (14 karakter). */
    static String generateTemporaryPassword() {
        StringBuilder sb = new StringBuilder(14);
        for (int group = 0; group < 3; group++) {
            if (group > 0) {
                sb.append('-');
            }
            sb.append(randomToken(PASSWORD_ALPHABET, 4));
        }
        return sb.toString();
    }

    private static String randomToken(char[] alphabet, int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(alphabet[RANDOM.nextInt(alphabet.length)]);
        }
        return sb.toString();
    }
}
