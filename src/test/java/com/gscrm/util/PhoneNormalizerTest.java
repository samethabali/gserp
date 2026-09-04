package com.gscrm.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneNormalizerTest {

    @Test
    void normalizesEveryCaseInTheTable() {
        for (Map.Entry<String, String> testCase : PhoneNormalizerCases.all().entrySet()) {
            assertThat(PhoneNormalizer.normalizeOrNull(testCase.getKey()))
                    .as("girdi: '%s'", testCase.getKey())
                    .isEqualTo(testCase.getValue());
        }
    }

    @Test
    void nullInputIsNotNormalizable() {
        assertThat(PhoneNormalizer.normalizeOrNull(null)).isNull();
        assertThat(PhoneNormalizer.normalize(null)).isEmpty();
    }

    @Test
    void differentSpellingsOfTheSameNumberCollapseToOneKey() {
        String canonical = PhoneNormalizer.normalizeOrNull("+905321234567");
        assertThat(PhoneNormalizer.normalizeOrNull("0532 123 45 67")).isEqualTo(canonical);
        assertThat(PhoneNormalizer.normalizeOrNull("05321234567")).isEqualTo(canonical);
        assertThat(PhoneNormalizer.normalizeOrNull("905321234567")).isEqualTo(canonical);
    }

    @Test
    void blankIsAllowedButGarbageIsRejected() {
        // Zorunluluk ayrı bir kısıtın işi: boş girdi bu kısıttan geçer.
        assertThat(PhoneNormalizer.isNormalizable(null)).isTrue();
        assertThat(PhoneNormalizer.isNormalizable("")).isTrue();
        assertThat(PhoneNormalizer.isNormalizable("   ")).isTrue();

        assertThat(PhoneNormalizer.isNormalizable("1234567")).isFalse();
        assertThat(PhoneNormalizer.isNormalizable("abc")).isFalse();
    }
}
