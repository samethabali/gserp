package com.gscrm.service;

import com.gscrm.model.VerificationCode;
import com.gscrm.repository.VerificationCodeRepository;
import com.gscrm.service.sms.SmsSender;
import com.gscrm.service.sms.SmsService;
import com.gscrm.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VerificationCodeServiceTest {

    private static final Long SALON_ID = 1L;
    private static final String RAW_PHONE = "0532 123 45 67";
    private static final String CANONICAL = "+905321234567";
    private static final String IP = "203.0.113.9";

    @Mock private VerificationCodeRepository verificationCodeRepository;
    @Mock private SalonSettingsService salonSettingsService;
    @Mock private SmsService smsService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private VerificationCodeService service;

    @BeforeEach
    void setUp() {
        TenantContext.setSalonId(SALON_ID);
        service = new VerificationCodeService(
                verificationCodeRepository, salonSettingsService, passwordEncoder, smsService);

        enableVerification(true);
        when(verificationCodeRepository.save(any(VerificationCode.class))).thenAnswer(i -> i.getArgument(0));
        when(smsService.send(anyString(), anyString(), anyString()))
                .thenReturn(SmsSender.SmsResult.ok("test"));
        when(salonSettingsService.get("salon.name", "Salon")).thenReturn("Güzellik Merkezi");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void enableVerification(boolean enabled) {
        when(salonSettingsService.get(VerificationCodeService.SETTING_ENABLED, "false"))
                .thenReturn(String.valueOf(enabled));
    }

    private void noPreviousCode() {
        when(verificationCodeRepository
                .findFirstBySalonIdAndPhoneNormalizedAndPurposeOrderByCreatedAtDesc(SALON_ID, CANONICAL, "BOOKING"))
                .thenReturn(Optional.empty());
    }

    private void previousCode(VerificationCode record) {
        when(verificationCodeRepository
                .findFirstBySalonIdAndPhoneNormalizedAndPurposeOrderByCreatedAtDesc(SALON_ID, CANONICAL, "BOOKING"))
                .thenReturn(Optional.of(record));
    }

    private VerificationCode issuedCode(String plainCode, LocalDateTime createdAt, LocalDateTime expiresAt) {
        return VerificationCode.builder()
                .id(1L).salonId(SALON_ID).phoneNormalized(CANONICAL)
                .codeHash(passwordEncoder.encode(plainCode))
                .purpose("BOOKING").attempts(0).maxAttempts(5)
                .createdAt(createdAt).expiresAt(expiresAt)
                .build();
    }

    // ─── Bayrak kapalı ───

    @Test
    void disabledFlagShortCircuitsWithoutTouchingTheDatabase() {
        enableVerification(false);

        VerificationCodeService.StartResult result = service.start(RAW_PHONE, IP);

        assertThat(result.enabled()).isFalse();
        assertThat(result.sent()).isFalse();
        verify(verificationCodeRepository, never()).save(any());
        verify(smsService, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void showcaseTenantsAlwaysHaveVerificationDisabled() {
        TenantContext.setShowcase(true);
        try {
            assertThat(service.isEnabled()).isFalse();
        } finally {
            TenantContext.setShowcase(false);
        }
    }

    // ─── Kod üretimi ───

    @Test
    void startGeneratesSixDigitCodeAndSendsIt() {
        noPreviousCode();

        VerificationCodeService.StartResult result = service.start(RAW_PHONE, IP);

        assertThat(result.enabled()).isTrue();
        assertThat(result.sent()).isTrue();

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(smsService).send(org.mockito.ArgumentMatchers.eq(CANONICAL), message.capture(), anyString());
        assertThat(Pattern.compile("\\b\\d{6}\\b").matcher(message.getValue()).find()).isTrue();
    }

    /** Türkçe karakter mesajı UCS-2'ye zorlar ve mesaj başı maliyeti ikiye katlar. */
    @Test
    void smsBodyIsAsciiFoldedToKeepSingleSegmentCost() {
        noPreviousCode();

        service.start(RAW_PHONE, IP);

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(smsService).send(anyString(), message.capture(), anyString());
        assertThat(message.getValue()).isEqualTo(
                message.getValue().replaceAll("[^\\x00-\\x7F]", ""));
    }

    @Test
    void unnormalizablePhoneIsRejectedWithoutSending() {
        VerificationCodeService.StartResult result = service.start("abc", IP);

        assertThat(result.sent()).isFalse();
        verify(smsService, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void resendIsBlockedDuringCooldown() {
        previousCode(issuedCode("123456", LocalDateTime.now().minusSeconds(10),
                LocalDateTime.now().plusSeconds(170)));

        VerificationCodeService.StartResult result = service.start(RAW_PHONE, IP);

        assertThat(result.sent()).isFalse();
        assertThat(result.resendAfterSeconds()).isPositive();
        verify(smsService, never()).send(anyString(), anyString(), anyString());
    }

    /** IP döndüren saldırgan tek numarayı dövemesin: kota veritabanında sayılır. */
    @Test
    void perPhoneIssueQuotaIsEnforced() {
        previousCode(issuedCode("123456", LocalDateTime.now().minusMinutes(5),
                LocalDateTime.now().minusMinutes(2)));
        when(verificationCodeRepository.countBySalonIdAndPhoneNormalizedAndCreatedAtAfter(
                anyLong(), anyString(), any())).thenReturn(3L);

        VerificationCodeService.StartResult result = service.start(RAW_PHONE, IP);

        assertThat(result.sent()).isFalse();
        verify(smsService, never()).send(anyString(), anyString(), anyString());
    }

    // ─── Doğrulama ───

    @Test
    void correctCodeReturnsSingleUseToken() {
        previousCode(issuedCode("123456", LocalDateTime.now(), LocalDateTime.now().plusMinutes(3)));

        VerificationCodeService.ConfirmResult result = service.confirm(RAW_PHONE, "123456", IP);

        assertThat(result.verified()).isTrue();
        assertThat(result.verificationToken()).isNotBlank();
    }

    @Test
    void wrongCodeFailsAndIncrementsAttempts() {
        VerificationCode record = issuedCode("123456", LocalDateTime.now(), LocalDateTime.now().plusMinutes(3));
        previousCode(record);

        VerificationCodeService.ConfirmResult result = service.confirm(RAW_PHONE, "000000", IP);

        assertThat(result.verified()).isFalse();
        assertThat(result.verificationToken()).isNull();
        assertThat(record.getAttempts()).isEqualTo(1);
    }

    @Test
    void expiredCodeIsRejected() {
        previousCode(issuedCode("123456", LocalDateTime.now().minusMinutes(10),
                LocalDateTime.now().minusMinutes(5)));

        assertThat(service.confirm(RAW_PHONE, "123456", IP).verified()).isFalse();
    }

    @Test
    void exhaustedAttemptsLockTheCodeEvenIfCorrect() {
        VerificationCode record = issuedCode("123456", LocalDateTime.now(), LocalDateTime.now().plusMinutes(3));
        record.setAttempts(5);
        previousCode(record);

        assertThat(service.confirm(RAW_PHONE, "123456", IP).verified()).isFalse();
    }

    /** Yanlış kod, süresi dolmuş kod ve tükenmiş deneme aynı mesajı dönmeli. */
    @Test
    void failureMessagesDoNotRevealWhichConditionFailed() {
        VerificationCode exhausted = issuedCode("123456", LocalDateTime.now(), LocalDateTime.now().plusMinutes(3));
        exhausted.setAttempts(5);
        previousCode(exhausted);
        String exhaustedMsg = service.confirm(RAW_PHONE, "123456", IP).message();

        previousCode(issuedCode("123456", LocalDateTime.now(), LocalDateTime.now().plusMinutes(3)));
        String wrongMsg = service.confirm(RAW_PHONE, "999999", IP).message();

        previousCode(issuedCode("123456", LocalDateTime.now().minusMinutes(9),
                LocalDateTime.now().minusMinutes(5)));
        String expiredMsg = service.confirm(RAW_PHONE, "123456", IP).message();

        assertThat(wrongMsg).isEqualTo(exhaustedMsg).isEqualTo(expiredMsg);
    }

    // ─── Token tüketimi ───

    @Test
    void tokenIsSingleUse() {
        VerificationCode record = issuedCode("123456", LocalDateTime.now(), LocalDateTime.now().plusMinutes(10));
        record.setVerifiedAt(LocalDateTime.now());
        record.setVerificationToken("tok");
        when(verificationCodeRepository.findByVerificationToken("tok")).thenReturn(Optional.of(record));

        assertThat(service.consume("tok")).contains(CANONICAL);
        assertThat(service.consume("tok")).isEmpty();
    }

    @Test
    void unknownOrUnverifiedTokenIsRejected() {
        when(verificationCodeRepository.findByVerificationToken("yok")).thenReturn(Optional.empty());
        assertThat(service.consume("yok")).isEmpty();
        assertThat(service.consume(null)).isEmpty();

        VerificationCode unverified = issuedCode("123456", LocalDateTime.now(), LocalDateTime.now().plusMinutes(10));
        unverified.setVerificationToken("tok2");
        when(verificationCodeRepository.findByVerificationToken("tok2")).thenReturn(Optional.of(unverified));
        assertThat(service.consume("tok2")).isEmpty();
    }

    /** Bir salonda alınan token başka salonda kullanılamaz. */
    @Test
    void tokenFromAnotherSalonIsRejected() {
        VerificationCode foreign = issuedCode("123456", LocalDateTime.now(), LocalDateTime.now().plusMinutes(10));
        foreign.setSalonId(999L);
        foreign.setVerifiedAt(LocalDateTime.now());
        foreign.setVerificationToken("tok3");
        when(verificationCodeRepository.findByVerificationToken("tok3")).thenReturn(Optional.of(foreign));

        assertThat(service.consume("tok3")).isEmpty();
    }

    @Test
    void purgeDeletesExpiredRows() {
        when(verificationCodeRepository.deleteExpiredBefore(any())).thenReturn(3);
        service.purgeExpired();
        verify(verificationCodeRepository).deleteExpiredBefore(any());
    }
}
