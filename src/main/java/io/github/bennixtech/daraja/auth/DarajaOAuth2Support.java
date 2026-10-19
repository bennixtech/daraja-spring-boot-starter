package io.github.bennixtech.daraja.auth;

import io.github.bennixtech.daraja.config.DarajaProperties;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Daraja-specific OAuth2 infrastructure, deliberately separate from application OAuth2 beans.
 * Credentials are application scoped, including when called from scheduled jobs.
 */
public final class DarajaOAuth2Support {
    private static final String REGISTRATION_ID = "daraja";
    private static final Authentication PRINCIPAL =
            new UsernamePasswordAuthenticationToken("daraja-sdk", null, List.of());
    private final InMemoryOAuth2AuthorizedClientService service;
    private final OAuth2ClientHttpRequestInterceptor interceptor;
    private final Object authorizationLock = new Object();

    public DarajaOAuth2Support(DarajaProperties properties, RestClient tokenClient) {
        ClientRegistration registration = ClientRegistration.withRegistrationId(REGISTRATION_ID)
                .clientId(properties.getConsumerKey())
                .clientSecret(properties.getConsumerSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenUri(properties.resolvedBaseUrl() + "/oauth/v1/generate?grant_type=client_credentials")
                .build();
        var repository = new InMemoryClientRegistrationRepository(registration);
        service = new InMemoryOAuth2AuthorizedClientService(repository);
        var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(repository, service);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials(configurer -> configurer
                        .accessTokenResponseClient(new DarajaTokenResponseClient(tokenClient)))
                .build());
        interceptor = new OAuth2ClientHttpRequestInterceptor(request -> {
            // Collapse concurrent token acquisition/renewal; payment HTTP calls are not serialized.
            synchronized (authorizationLock) {
                return manager.authorize(request);
            }
        });
        interceptor.setClientRegistrationIdResolver(request -> REGISTRATION_ID);
        interceptor.setPrincipalResolver(request -> PRINCIPAL);
        interceptor.setAuthorizationFailureHandler(OAuth2ClientHttpRequestInterceptor.authorizationFailureHandler(service));
    }

    public OAuth2ClientHttpRequestInterceptor interceptor() {
        return interceptor;
    }

    /** Invalidates a rejected token for the NEXT call; payment requests are never automatically replayed. */
    public void clearToken() {
        synchronized (authorizationLock) {
            service.removeAuthorizedClient(REGISTRATION_ID, PRINCIPAL.getName());
        }
    }
}
