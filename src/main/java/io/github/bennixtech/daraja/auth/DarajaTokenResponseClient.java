package io.github.bennixtech.daraja.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2ClientCredentialsGrantRequest;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.net.URI;
import java.nio.charset.StandardCharsets;

/** Adapts Daraja's GET/Basic token endpoint to Spring Security's client-credentials support. */
public final class DarajaTokenResponseClient
        implements OAuth2AccessTokenResponseClient<OAuth2ClientCredentialsGrantRequest> {
    private final DarajaAuthApiClient authClient;

    public DarajaTokenResponseClient(RestClient restClient) {
        this.authClient = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
                .build().createClient(DarajaAuthApiClient.class);
    }

    @Override
    public OAuth2AccessTokenResponse getTokenResponse(OAuth2ClientCredentialsGrantRequest request) {
        var registration = request.getClientRegistration();
        try {
            String authorization = "Basic " + HttpHeaders.encodeBasicAuth(
                    registration.getClientId(), registration.getClientSecret(), StandardCharsets.UTF_8);
            TokenResponse token = authClient.generateAccessToken(
                    URI.create(registration.getProviderDetails().getTokenUri()), authorization);
            if (token == null || token.accessToken() == null || token.accessToken().isBlank()) {
                throw new IllegalArgumentException("Missing access token");
            }
            long expiresIn = Long.parseLong(token.expiresIn());
            if (expiresIn <= 0) {
                throw new IllegalArgumentException("Invalid token lifetime");
            }
            return OAuth2AccessTokenResponse.withToken(token.accessToken())
                    .tokenType(OAuth2AccessToken.TokenType.BEARER)
                    .expiresIn(expiresIn)
                    .build();
        } catch (RestClientException | IllegalArgumentException ex) {
            // Do not expose the token response or credentials in error messages.
            throw new OAuth2AuthorizationException(
                    new OAuth2Error("invalid_token_response", "Unable to obtain a valid Daraja access token", null));
        }
    }

    @Builder(toBuilder = true)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") String expiresIn) {
        @Override
        public String toString() {
            return "TokenResponse[accessToken=[REDACTED], expiresIn=" + expiresIn + "]";
        }
    }
}
