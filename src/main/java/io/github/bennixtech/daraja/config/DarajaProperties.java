package io.github.bennixtech.daraja.config;

import io.github.bennixtech.daraja.DarajaEnvironment;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

@Getter
@Setter
@Validated
@ConfigurationProperties("daraja")
public class DarajaProperties {
    /** Disable all Daraja auto-configuration. */
    private boolean enabled = true;
    @NotNull
    private DarajaEnvironment environment = DarajaEnvironment.SANDBOX;
    @NotBlank
    private String consumerKey;
    @NotBlank
    private String consumerSecret;
    /** Optional endpoint override, primarily for testing or a gateway. */
    private URI baseUrl;
    @NotNull
    private Duration connectTimeout = Duration.ofSeconds(10);
    @NotNull
    private Duration readTimeout = Duration.ofSeconds(60);

    public String resolvedBaseUrl() {
        return baseUrl == null ? environment.getBaseUrl() : baseUrl.toString().replaceAll("/+$", "");
    }

    @AssertTrue(message = "connect-timeout and read-timeout must be positive")
    public boolean isTimeoutsValid() {
        return connectTimeout != null && !connectTimeout.isNegative() && !connectTimeout.isZero()
                && readTimeout != null && !readTimeout.isNegative() && !readTimeout.isZero();
    }

    @AssertTrue(message = "base-url must be an absolute HTTP(S) URL without credentials, query, or fragment")
    public boolean isBaseUrlValid() {
        return baseUrl == null || (baseUrl.isAbsolute() && baseUrl.getHost() != null
                && ("https".equalsIgnoreCase(baseUrl.getScheme()) || "http".equalsIgnoreCase(baseUrl.getScheme()))
                && baseUrl.getUserInfo() == null && baseUrl.getQuery() == null && baseUrl.getFragment() == null);
    }
}
