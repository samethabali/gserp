package com.gscrm.util;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Java ve SQL normalizasyon kurallarının aynı sonucu verdiğini doğrular.
 *
 * <p>Kurallar zorunlu olarak iki yerde yaşıyor: uygulama içindeki
 * {@link PhoneNormalizer} ve V30'daki {@code gscrm_normalize_phone} fonksiyonu
 * (geriye dönük doldurma ve yinelenen tespiti SQL tarafında yapılıyor). Bu test,
 * ikisinin sessizce ayrışmasını engelleyen tek mekanizmadır — isteğe bağlı değildir.
 */
@SpringBootTest
@ActiveProfiles("test")
class PhoneNormalizerSqlParityIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void sqlFunctionMatchesJavaImplementation() {
        for (Map.Entry<String, String> testCase : PhoneNormalizerCases.all().entrySet()) {
            String input = testCase.getKey();

            String fromSql = jdbcTemplate.queryForObject(
                    "SELECT gscrm_normalize_phone(?)", String.class, input);
            String fromJava = PhoneNormalizer.normalizeOrNull(input);

            assertThat(fromSql)
                    .as("SQL ile Java ayrıştı — girdi: '%s'", input)
                    .isEqualTo(fromJava);
        }
    }

    @Test
    void sqlFunctionHandlesNull() {
        String result = jdbcTemplate.queryForObject(
                "SELECT gscrm_normalize_phone(NULL::text)", String.class);
        assertThat(result).isNull();
    }
}
