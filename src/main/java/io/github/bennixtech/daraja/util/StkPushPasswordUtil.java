package io.github.bennixtech.daraja.util;

import lombok.Builder;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Base64;

/** Password and timestamp generation uses Africa/Nairobi regardless of the server's default timezone. */
public final class StkPushPasswordUtil {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("uuuuMMddHHmmss")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final ZoneId NAIROBI = ZoneId.of("Africa/Nairobi");

    private StkPushPasswordUtil() {}

    public static String generateTimestamp() {
        return generateTimestamp(LocalDateTime.now(NAIROBI));
    }

    public static String generateTimestamp(LocalDateTime dateTime) {
        return dateTime.format(FORMAT);
    }

    public static PasswordResult generatePassword(String businessShortCode, String passkey) {
        return generatePassword(businessShortCode, passkey, Clock.system(NAIROBI));
    }

    public static PasswordResult generatePassword(String businessShortCode, String passkey, Clock clock) {
        String timestamp = generateTimestamp(LocalDateTime.ofInstant(clock.instant(), NAIROBI));
        return new PasswordResult(generatePassword(businessShortCode, passkey, timestamp), timestamp);
    }

    public static String generatePassword(String businessShortCode, String passkey, String timestamp) {
        if (businessShortCode == null || businessShortCode.isBlank() || passkey == null || passkey.isBlank()) {
            throw new IllegalArgumentException("Business short code and passkey are required");
        }
        if (timestamp == null || !timestamp.matches("[0-9]{14}")) {
            throw new IllegalArgumentException("Timestamp must use yyyyMMddHHmmss format");
        }
        LocalDateTime.parse(timestamp, FORMAT);
        return Base64.getEncoder().encodeToString(
                (businessShortCode + passkey + timestamp).getBytes(StandardCharsets.UTF_8));
    }

    @Builder(toBuilder = true)
    public record PasswordResult(String password, String timestamp) {
        public String getPassword() { return password; }
        public String getTimestamp() { return timestamp; }

        @Override
        public String toString() {
            return "PasswordResult[password=[REDACTED], timestamp=" + timestamp + "]";
        }
    }
}
