package com.gscrm.service;

import com.gscrm.model.VerificationCode;
import com.gscrm.repository.VerificationCodeRepository;
import com.gscrm.service.sms.SmsService;
import com.gscrm.tenant.TenantContext;
import com.gscrm.util.PhoneNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Telefon doğrulama kodlarının üretimi, doğrulanması ve tüketilmesi.
 *
 * <p><b>Numara ifşasına karşı:</b> {@link #start} bilinen ve bilinmeyen numara için
 * birebir aynı yanıtı döner ve her hâlükârda kod üretip gönderir; yanıttan önce
 * müşteri varlığına hiç dallanılmaz. İsim yalnızca doğru kodla açığa çıkar, o da
 * numaraya fiilen sahip olmayı gerektirir.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VerificationCodeService {

    public static final String SETTING_ENABLED = "booking.sms_verification_enabled";

    private static final String PURPOSE_BOOKING = "BOOKING";
    private static final Duration CODE_TTL = Duration.ofSeconds(180);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final Duration TOKEN_TTL = Duration.ofMinutes(15);
    private static final Duration ISSUE_WINDOW = Duration.ofMinutes(15);
    private static final int MAX_CODES_PER_WINDOW = 3;
    private static final int MAX_ATTEMPTS = 5;
    /** Süresi geçen kayıtlar bu kadar süre sonra silinir. */
    private static final Duration PURGE_AFTER = Duration.ofDays(1);

    private final SecureRandom random = new SecureRandom();

    private final VerificationCodeRepository verificationCodeRepository;
    private final SalonSettingsService salonSettingsService;
    private final PasswordEncoder passwordEncoder;
    private final SmsService smsService;

    /** {@link #start} sonucu — bilinen/bilinmeyen numara ayrımı yapmaz. */
    public record StartResult(boolean enabled, boolean sent, long resendAfterSeconds,
                              long expiresInSeconds, String message) {
    }

    /** {@link #confirm} sonucu; başarısızlıkta token null olur. */
    public record ConfirmResult(boolean verified, String verificationToken, String message) {
    }

    /**
     * Doğrulama bu salon için açık mı?
     *
     * <p>Showcase (demo) tenant'larda zorla kapalı: demo salonlar gerçek gönderim
     * denemesi yapmamalı.
     */
    public boolean isEnabled() {
        if (TenantContext.isShowcase()) return false;
        return Boolean.parseBoolean(salonSettingsService.get(SETTING_ENABLED, "false"));
    }

    @Transactional
    public StartResult start(String rawPhone, String clientIp) {
        if (!isEnabled()) {
            return new StartResult(false, false, 0, 0, null);
        }

        String normalized = PhoneNormalizer.normalizeOrNull(rawPhone);
        if (normalized == null) {
            return new StartResult(true, false, 0, 0, "Geçerli bir telefon numarası girin");
        }

        Long salonId = TenantContext.requireSalonId();
        LocalDateTime now = LocalDateTime.now();

        Optional<VerificationCode> latest = verificationCodeRepository
                .findFirstBySalonIdAndPhoneNormalizedAndPurposeOrderByCreatedAtDesc(
                        salonId, normalized, PURPOSE_BOOKING);

        // Tekrar gönderme bekleme süresi
        if (latest.isPresent()) {
            LocalDateTime canResendAt = latest.get().getCreatedAt().plus(RESEND_COOLDOWN);
            if (now.isBefore(canResendAt)) {
                long wait = Duration.between(now, canResendAt).getSeconds();
                return new StartResult(true, false, wait, 0,
                        "Yeni kod için " + wait + " saniye bekleyin");
            }
        }

        // Numara başına üretim kotası — IP döndüren saldırgan tek numarayı dövemesin.
        long issued = verificationCodeRepository.countBySalonIdAndPhoneNormalizedAndCreatedAtAfter(
                salonId, normalized, now.minus(ISSUE_WINDOW));
        if (issued >= MAX_CODES_PER_WINDOW) {
            return new StartResult(true, false, RESEND_COOLDOWN.getSeconds(), 0,
                    "Bu numara için çok fazla kod istendi. Lütfen daha sonra tekrar deneyin.");
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        verificationCodeRepository.save(VerificationCode.builder()
                .salonId(salonId)
                .phoneNormalized(normalized)
                .codeHash(passwordEncoder.encode(code))
                .purpose(PURPOSE_BOOKING)
                .attempts(0)
                .maxAttempts(MAX_ATTEMPTS)
                .requestIp(clientIp)
                .createdAt(now)
                .expiresAt(now.plus(CODE_TTL))
                .build());

        smsService.send(normalized, buildMessage(code), "BOOKING_VERIFICATION");

        return new StartResult(true, true, RESEND_COOLDOWN.getSeconds(), CODE_TTL.getSeconds(),
                "Doğrulama kodu gönderildi");
    }

    @Transactional
    public ConfirmResult confirm(String rawPhone, String code, String clientIp) {
        if (!isEnabled()) {
            return new ConfirmResult(false, null, "Doğrulama bu salon için kapalı");
        }

        String normalized = PhoneNormalizer.normalizeOrNull(rawPhone);
        if (normalized == null || code == null || code.isBlank()) {
            return new ConfirmResult(false, null, "Kod hatalı veya süresi dolmuş");
        }

        Long salonId = TenantContext.requireSalonId();
        LocalDateTime now = LocalDateTime.now();

        VerificationCode record = verificationCodeRepository
                .findFirstBySalonIdAndPhoneNormalizedAndPurposeOrderByCreatedAtDesc(
                        salonId, normalized, PURPOSE_BOOKING)
                .orElse(null);

        // Yanlış kod, tükenmiş deneme ve süresi dolmuş kayıt aynı mesajı döner:
        // saldırgan hangi durumda olduğunu öğrenmemeli.
        if (record == null || record.isExpired(now) || record.isAttemptsExhausted()
                || record.getConsumedAt() != null) {
            return new ConfirmResult(false, null, "Kod hatalı veya süresi dolmuş");
        }

        record.setAttempts(record.getAttempts() + 1);
        if (!passwordEncoder.matches(code.trim(), record.getCodeHash())) {
            verificationCodeRepository.save(record);
            return new ConfirmResult(false, null, "Kod hatalı veya süresi dolmuş");
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        record.setVerifiedAt(now);
        record.setVerificationToken(token);
        // Token ömrü koddan uzun: müşteri doğruladıktan sonra formu doldurmaya vakti olsun.
        record.setExpiresAt(now.plus(TOKEN_TTL));
        record.setRequestIp(clientIp);
        verificationCodeRepository.save(record);

        return new ConfirmResult(true, token, "Numaranız doğrulandı");
    }

    /**
     * Token'ı tek kullanımlık olarak harcar.
     *
     * @return doğrulanmış normalize telefon, geçersizse boş
     */
    @Transactional
    public Optional<String> consume(String verificationToken) {
        if (verificationToken == null || verificationToken.isBlank()) return Optional.empty();

        Long salonId = TenantContext.requireSalonId();
        LocalDateTime now = LocalDateTime.now();

        VerificationCode record = verificationCodeRepository.findByVerificationToken(verificationToken)
                .orElse(null);

        if (record == null
                || !salonId.equals(record.getSalonId())   // token başka bir salona ait olamaz
                || record.getVerifiedAt() == null
                || record.getConsumedAt() != null
                || record.isExpired(now)) {
            return Optional.empty();
        }

        record.setConsumedAt(now);
        verificationCodeRepository.save(record);
        return Optional.of(record.getPhoneNormalized());
    }

    private String buildMessage(String code) {
        String salonName = salonSettingsService.get("salon.name", "Salon");
        // Türkçe karakter mesajı UCS-2'ye zorlar: segment 160'tan 70 karaktere düşer ve
        // mesaj başı maliyet ikiye katlanır. Şablon bilerek ASCII'ye katlanmış.
        return asciiFold(salonName) + " randevu dogrulama kodunuz: " + code
                + ". 3 dakika gecerlidir. Kodu kimseyle paylasmayin.";
    }

    private static String asciiFold(String value) {
        if (value == null) return "";
        return value
                .replace('ç', 'c').replace('Ç', 'C')
                .replace('ğ', 'g').replace('Ğ', 'G')
                .replace('ı', 'i').replace('İ', 'I')
                .replace('ö', 'o').replace('Ö', 'O')
                .replace('ş', 's').replace('Ş', 'S')
                .replace('ü', 'u').replace('Ü', 'U');
    }

    /** Süresi geçmiş kodları temizler (RetentionJob ile aynı saat). */
    @Scheduled(cron = "0 35 3 * * *")
    @Transactional
    public void purgeExpired() {
        int removed = verificationCodeRepository.deleteExpiredBefore(LocalDateTime.now().minus(PURGE_AFTER));
        if (removed > 0) {
            log.info("Süresi geçmiş {} doğrulama kodu silindi", removed);
        }
    }
}
