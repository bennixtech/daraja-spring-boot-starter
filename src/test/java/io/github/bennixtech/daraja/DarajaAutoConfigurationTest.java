package io.github.bennixtech.daraja;

import io.github.bennixtech.daraja.auth.DarajaOAuth2Support;
import io.github.bennixtech.daraja.config.DarajaAutoConfiguration;
import io.github.bennixtech.daraja.config.DarajaProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.aop.support.AopUtils;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import io.github.bennixtech.daraja.stkpush.StkPushRequest;
import jakarta.validation.ConstraintViolationException;

class DarajaAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class,
                    ValidationAutoConfiguration.class, RestClientAutoConfiguration.class,
                    DarajaAutoConfiguration.class));

    private ApplicationContextRunner configured() {
        return runner.withPropertyValues("daraja.consumer-key=test-key", "daraja.consumer-secret=test-secret");
    }

    @Test
    void createsClientAndScopesOAuthInfrastructureToDaraja() {
        configured().run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(DarajaApiClient.class)
                    .hasSingleBean(DarajaOAuth2Support.class).hasBean("darajaRestClient");
            assertThat(context.getBean("darajaRestClient")).isInstanceOf(RestClient.class);
            assertThat(AopUtils.isJdkDynamicProxy(context.getBean(DarajaApiClient.class))).isTrue();
            assertThat(context).doesNotHaveBean(ClientRegistrationRepository.class);
            assertThat(context.getBean(DarajaProperties.class).getEnvironment()).isEqualTo(DarajaEnvironment.SANDBOX);
        });
    }

    @Test
    void coexistsWithApplicationOAuthRegistrationRepository() {
        ClientRegistrationRepository applicationRepository = id -> null;
        configured().withBean(ClientRegistrationRepository.class, () -> applicationRepository)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(DarajaApiClient.class);
                    assertThat(context.getBean(ClientRegistrationRepository.class)).isSameAs(applicationRepository);
                });
    }

    @Test
    void autoConfiguredProxyValidatesWithoutCallingHttp() {
        configured().run(context -> {
            var client = context.getBean(DarajaApiClient.class);
            assertThatThrownBy(() -> client.initiateStkPush(null))
                    .isInstanceOf(ConstraintViolationException.class);
            var request = StkPushRequest.builder().businessShortCode("174379").password("encoded")
                    .timestamp("20261019133000").amount(1).phoneNumber("0712345678")
                    .callBackURL("https://localhost/callback").accountReference("invoice-1")
                    .transactionDesc("Payment").build();
            assertThatThrownBy(() -> client.initiateStkPush(request))
                    .isInstanceOf(ConstraintViolationException.class);
        });
    }

    @Test
    void autoConfiguredValidatorUsesProductionCallbackPolicy() {
        configured().withPropertyValues("daraja.environment=PRODUCTION").run(context -> {
            var request = StkPushRequest.builder().businessShortCode("174379").password("encoded")
                    .timestamp("20261019133000").amount(1).phoneNumber("0712345678")
                    .callBackURL("http://example.com/callback").accountReference("invoice-1")
                    .transactionDesc("Payment").build();
            assertThatThrownBy(() -> context.getBean(DarajaApiClient.class).initiateStkPush(request))
                    .isInstanceOf(ConstraintViolationException.class);
        });
    }

    @Test
    void disabledStarterDoesNotRequireCredentialsOrCreateInfrastructure() {
        runner.withPropertyValues("daraja.enabled=false").run(context ->
                assertThat(context).hasNotFailed().doesNotHaveBean(DarajaApiClient.class)
                        .doesNotHaveBean(DarajaOAuth2Support.class).doesNotHaveBean(DarajaProperties.class));
    }

    @Test
    void failsFastForMissingCredentials() {
        runner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsInvalidTimeoutAndEndpoint() {
        configured().withPropertyValues("daraja.read-timeout=0s")
                .run(context -> assertThat(context).hasFailed());
        configured().withPropertyValues("daraja.base-url=https://user:password@example.com")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void backsOffCompletelyForApplicationProvidedClient() {
        DarajaApiClient custom = (DarajaApiClient) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{DarajaApiClient.class}, (proxy, method, args) -> null);
        runner.withBean(DarajaApiClient.class, () -> custom).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(DarajaApiClient.class)
                    .doesNotHaveBean(DarajaOAuth2Support.class);
            assertThat(context.getBean(DarajaApiClient.class)).isSameAs(custom);
        });
    }

    @Test
    void autoConfigurationIsRegisteredForDiscovery() {
        assertThat(ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader()).getCandidates())
                .contains(DarajaAutoConfiguration.class.getName());
    }
}
