package io.github.bennixtech.daraja.config;

import io.github.bennixtech.daraja.DarajaApiClient;
import io.github.bennixtech.daraja.auth.DarajaOAuth2Support;
import io.github.bennixtech.daraja.error.DarajaResponseErrorHandler;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;

@AutoConfiguration(after = RestClientAutoConfiguration.class)
@ConditionalOnClass({RestClient.class, OAuth2ClientHttpRequestInterceptor.class})
@ConditionalOnProperty(prefix = "daraja", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnMissingBean(DarajaApiClient.class)
@EnableConfigurationProperties(DarajaProperties.class)
public class DarajaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    DarajaOAuth2Support darajaOAuth2Support(RestClient.Builder builder, DarajaProperties properties,
                                          @Qualifier("darajaHttpClient") HttpClient httpClient) {
        return new DarajaOAuth2Support(properties, configuredBuilder(builder, properties, httpClient).build());
    }

    @Bean(name = "darajaHttpClient", destroyMethod = "close")
    @ConditionalOnMissingBean(name = "darajaHttpClient")
    HttpClient darajaHttpClient(DarajaProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Bean("darajaRestClient")
    @ConditionalOnMissingBean(name = "darajaRestClient")
    RestClient darajaRestClient(RestClient.Builder builder, DarajaProperties properties,
                               DarajaOAuth2Support oauth2, JsonMapper jsonMapper,
                               @Qualifier("darajaHttpClient") HttpClient httpClient) {
        return configuredBuilder(builder, properties, httpClient)
                .baseUrl(properties.resolvedBaseUrl())
                .requestInterceptor(oauth2.interceptor())
                .defaultStatusHandler(status -> status.isError(),
                        new DarajaResponseErrorHandler(jsonMapper, oauth2))
                .build();
    }

    @Bean
    DarajaApiClient darajaApiClient(@Qualifier("darajaRestClient") RestClient restClient,
                                   Validator validator) {
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
                .proxyFactoryCustomizer((factory, serviceType) -> factory.addAdvice(0,
                        new MethodValidationInterceptor(validator)))
                .build().createClient(DarajaApiClient.class);
    }

    private RestClient.Builder configuredBuilder(RestClient.Builder builder, DarajaProperties properties,
                                                 HttpClient httpClient) {
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getReadTimeout());
        return builder.clone().requestFactory(requestFactory);
    }
}
