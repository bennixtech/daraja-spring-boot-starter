package io.github.bennixtech.daraja.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/** Callback URL structure, with stricter HTTPS/path checks in the configured production environment. */
@Target({FIELD, METHOD, PARAMETER, RECORD_COMPONENT, ANNOTATION_TYPE, TYPE_USE})
@Retention(RUNTIME)
@Constraint(validatedBy = CallbackUrlConstraintValidator.class)
public @interface CallbackUrl {
    String message() default "must be a valid public callback URL (HTTPS with a specific path in production)";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
