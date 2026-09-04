package com.gscrm.validation;

import com.gscrm.util.PhoneNormalizer;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Boş girdi serbest (zorunluluk ayrı bir kısıtın işi); dolu girdi çözümlenebilmeli. */
public class PhoneNumberValidator implements ConstraintValidator<PhoneNumber, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return PhoneNormalizer.isNormalizable(value);
    }
}
