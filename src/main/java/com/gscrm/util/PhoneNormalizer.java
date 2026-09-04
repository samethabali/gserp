package com.gscrm.util;

import java.util.Optional;

/**
 * Telefon numarasını kanonik E.164 biçimine ({@code +905321234567}) çevirir.
 *
 * <p>Telefon bu üründe fiilî müşteri kimliğidir: randevu geçmişi, sadakat, kupon ve
 * aktivite kaydı hep onun üzerinden eşleşir. Normalizasyon olmadan
 * {@code 0532 111 22 33} ile {@code +905321112233} farklı müşteri sayılır.
 *
 * <p><b>Güvenlik özelliği:</b> çözümlenemeyen girdi boş döner, kolona {@code NULL}
 * yazılır ve eşleştirme sorguları null'da kısa devre yapar. Yani çöp girdi iki
 * yabancıyı asla birbirine bağlayamaz — doğrulamasız sessiz eşleştirmeyi güvenli
 * kılan şey budur.
 *
 * <p>Kurallar {@code db/migration/V30__phone_normalization.sql} içindeki
 * {@code gscrm_normalize_phone} fonksiyonuyla birebir aynıdır; ikisinin ayrışmasını
 * {@code PhoneNormalizerSqlParityIT} engeller.
 */
public final class PhoneNormalizer {

    private static final String TR_COUNTRY_CODE = "90";
    /** TR ulusal anlamlı numara: 10 hane, ilk hane 2-5 (sabit hat + mobil). */
    private static final int TR_NSN_LENGTH = 10;
    private static final int MIN_INTL_DIGITS = 8;
    private static final int MAX_INTL_DIGITS = 15;

    private PhoneNormalizer() {
    }

    /** Kanonik biçim, çözümlenemezse {@link Optional#empty()}. */
    public static Optional<String> normalize(String raw) {
        if (raw == null) return Optional.empty();
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return Optional.empty();

        boolean international = trimmed.startsWith("+");
        String digits = trimmed.replaceAll("[^0-9]", "");

        if (digits.startsWith("00")) {
            international = true;
            digits = digits.substring(2);
        }

        if (international) {
            // Ülke kodu 90 ise TR kurallarını uygula; yabancı numarayı uzunluğa göre kabul et.
            if (digits.startsWith(TR_COUNTRY_CODE)) {
                String nsn = digits.substring(TR_COUNTRY_CODE.length());
                return isValidTrNsn(nsn) ? Optional.of("+" + TR_COUNTRY_CODE + nsn) : Optional.empty();
            }
            return digits.length() >= MIN_INTL_DIGITS && digits.length() <= MAX_INTL_DIGITS
                    ? Optional.of("+" + digits)
                    : Optional.empty();
        }

        // Ulusal yazımlar: 905321234567 / 0905321234567 / 05321234567 / 5321234567
        String nsn = null;
        if (digits.length() == TR_COUNTRY_CODE.length() + TR_NSN_LENGTH && digits.startsWith(TR_COUNTRY_CODE)) {
            nsn = digits.substring(TR_COUNTRY_CODE.length());
        } else if (digits.length() == 1 + TR_COUNTRY_CODE.length() + TR_NSN_LENGTH
                && digits.startsWith("0" + TR_COUNTRY_CODE)) {
            nsn = digits.substring(1 + TR_COUNTRY_CODE.length());
        } else if (digits.length() == 1 + TR_NSN_LENGTH && digits.startsWith("0")) {
            nsn = digits.substring(1);
        } else if (digits.length() == TR_NSN_LENGTH) {
            nsn = digits;
        }

        return isValidTrNsn(nsn) ? Optional.of("+" + TR_COUNTRY_CODE + nsn) : Optional.empty();
    }

    /** Kanonik biçim ya da {@code null} — kolona yazmak için. */
    public static String normalizeOrNull(String raw) {
        return normalize(raw).orElse(null);
    }

    /** Doğrulama kısıtı için: boş girdi serbest, dolu girdi çözümlenebilmeli. */
    public static boolean isNormalizable(String raw) {
        if (raw == null || raw.trim().isEmpty()) return true;
        return normalize(raw).isPresent();
    }

    private static boolean isValidTrNsn(String nsn) {
        if (nsn == null || nsn.length() != TR_NSN_LENGTH) return false;
        char first = nsn.charAt(0);
        return first >= '2' && first <= '5';
    }
}
