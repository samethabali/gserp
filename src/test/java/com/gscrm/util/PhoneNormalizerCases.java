package com.gscrm.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Normalizasyon vaka tablosu — hem Java testinin hem SQL eşitlik testinin kaynağı.
 *
 * <p>Tek tablo olması şart: kurallar iki yerde (Java sınıfı ve V30'daki plpgsql
 * fonksiyonu) yaşıyor ve tek kopya vaka listesi, ikisinin ayrışmasını yakalayan
 * mekanizmanın ta kendisi.
 */
final class PhoneNormalizerCases {

    private PhoneNormalizerCases() {
    }

    /** Girdi → beklenen kanonik değer ({@code null} = çözümlenemez). */
    static Map<String, String> all() {
        Map<String, String> cases = new LinkedHashMap<>();

        // Aynı TR mobil numarasının yaygın yazımları — hepsi tek değere düşmeli
        cases.put("05321234567", "+905321234567");
        cases.put("0532 123 45 67", "+905321234567");
        cases.put("0532-123-45-67", "+905321234567");
        cases.put("(0532) 123 45 67", "+905321234567");
        cases.put("5321234567", "+905321234567");
        cases.put("532 123 45 67", "+905321234567");
        cases.put("905321234567", "+905321234567");
        cases.put("+905321234567", "+905321234567");
        cases.put("+90 532 123 45 67", "+905321234567");
        cases.put("00905321234567", "+905321234567");
        cases.put("0090 532 123 45 67", "+905321234567");
        cases.put("0905321234567", "+905321234567");
        cases.put("  0532 123 45 67  ", "+905321234567");

        // TR sabit hatlar — eski panel regex'i bunları haksız yere reddediyordu
        cases.put("02123334455", "+902123334455");
        cases.put("0212 333 44 55", "+902123334455");
        cases.put("+902123334455", "+902123334455");
        cases.put("03123334455", "+903123334455");
        cases.put("04443334455", "+904443334455");

        // Yabancı numaralar
        cases.put("+4915112345678", "+4915112345678");
        cases.put("004915112345678", "+4915112345678");
        cases.put("+12125550123", "+12125550123");

        // Çözümlenemeyenler → null (eşleştirme anahtarı olmamalı)
        cases.put("", null);
        cases.put("   ", null);
        cases.put("abc", null);
        cases.put("12345", null);
        cases.put("1234567", null);
        cases.put("+", null);
        cases.put("0", null);
        cases.put("01234567890", null);          // 0 + 10 hane ama ilk hane 1
        cases.put("06321234567", null);          // ilk hane 6
        cases.put("+9053212345", null);          // TR ülke kodu, eksik hane
        cases.put("+905321234567890", null);     // TR ülke kodu, fazla hane
        cases.put("053212345678", null);         // 12 hane, hiçbir ulusal kalıba uymaz
        cases.put("+491", null);                 // uluslararası ama çok kısa

        return cases;
    }
}
