package io.github.bennixtech.daraja.validation;

import io.github.bennixtech.daraja.DarajaEnvironment;
import io.github.bennixtech.daraja.config.DarajaProperties;
import io.github.bennixtech.daraja.util.CallbackUrlValidator;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Instantiated and injected by Spring's ConstraintValidatorFactory. */
public final class CallbackUrlConstraintValidator implements ConstraintValidator<CallbackUrl, String> {
    private final DarajaProperties properties;

    public CallbackUrlConstraintValidator(DarajaProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Requiredness belongs to @NotBlank; avoid duplicate messages for missing values.
        if (value == null || value.isBlank()) return true;
        return (properties.getEnvironment() == DarajaEnvironment.PRODUCTION
                ? CallbackUrlValidator.validateForProduction(value)
                : CallbackUrlValidator.validate(value)).isValid();
    }
}
