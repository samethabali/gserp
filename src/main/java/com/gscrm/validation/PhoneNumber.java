package com.gscrm.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Telefon numarası kısıtı — panel ve online randevu için <b>tek</b> tanım.
 *
 * <p>Eskiden iki ayrı regex vardı: panelde yalnız TR mobil kabul eden katı bir kalıp,
 * public randevuda ise neredeyse her şeyi geçiren gevşek bir kalıp. Panelin
 * reddedeceği numara public uçtan girilebiliyordu. Doğrulama artık normalizasyonu
 * yapan fonksiyonun kendisine dayanıyor: "sistemin anladığı numara" tek bir yerde
 * tanımlı.
 */
@Documented
@Constraint(validatedBy = PhoneNumberValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface PhoneNumber {

    String message() default "Geçerli bir telefon numarası girin (örn. 0532 123 45 67)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
