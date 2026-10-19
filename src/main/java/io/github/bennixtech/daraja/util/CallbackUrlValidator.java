package io.github.bennixtech.daraja.util;

import lombok.Builder;

import java.net.URI;
import java.util.Locale;

/** Structural URL checks only: no DNS lookups or claims of reachability are made. */
public final class CallbackUrlValidator {
    private CallbackUrlValidator() {}

    public static ValidationResult validate(String url) {
        if (url == null || url.isBlank()) return ValidationResult.invalid("Callback URL is required");
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                return ValidationResult.invalid("Callback URL must use HTTP or HTTPS");
            }
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) {
                return ValidationResult.invalid("Callback URL must have a host and no credentials or fragment");
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            if (host.equals("localhost") || host.endsWith(".localhost") || host.endsWith(".local")
                    || host.equals("[::1]") || host.equals("[::]") || host.startsWith("127.")
                    || host.startsWith("0.") || host.startsWith("10.") || host.startsWith("192.168.")
                    || host.startsWith("169.254.") || host.matches("172\\.(1[6-9]|2[0-9]|3[01])\\..*")
                    || host.startsWith("[fc") || host.startsWith("[fd") || host.startsWith("[fe80:")) {
                return ValidationResult.invalid("Callback URL must use a public host");
            }
            if ("http".equalsIgnoreCase(scheme)) {
                return ValidationResult.warning("HTTPS is recommended for callback URLs");
            }
            if (uri.getPath() == null || uri.getPath().isBlank() || uri.getPath().equals("/")) {
                return ValidationResult.warning("A specific callback path is recommended");
            }
            return ValidationResult.valid("Callback URL format is valid");
        } catch (IllegalArgumentException ex) {
            return ValidationResult.invalid("Malformed callback URL");
        }
    }

    public static ValidationResult validateForProduction(String url) {
        ValidationResult basic = validate(url);
        if (!basic.isValid()) return basic;
        URI uri = URI.create(url);
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            return ValidationResult.invalid("Production callbacks require HTTPS");
        }
        if (uri.getPath() == null || uri.getPath().isBlank() || uri.getPath().equals("/")) {
            return ValidationResult.invalid("Production callbacks require a specific path");
        }
        return ValidationResult.valid("Callback URL format meets production requirements");
    }

    @Builder(toBuilder = true)
    public record ValidationResult(boolean valid, boolean warning, String message) {
        public boolean isValid() { return valid; }
        public boolean isWarning() { return warning; }
        public String getMessage() { return message; }
        public static ValidationResult valid(String message) { return new ValidationResult(true, false, message); }
        public static ValidationResult warning(String message) { return new ValidationResult(true, true, message); }
        public static ValidationResult invalid(String message) { return new ValidationResult(false, false, message); }
    }
}
