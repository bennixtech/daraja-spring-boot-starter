package io.github.bennixtech.daraja;

import io.github.bennixtech.daraja.accountbalance.AccountBalanceRequest;
import io.github.bennixtech.daraja.auth.DarajaOAuth2Support;
import io.github.bennixtech.daraja.b2b.B2BRequest;
import io.github.bennixtech.daraja.b2c.B2CRequest;
import io.github.bennixtech.daraja.c2b.C2BRegisterUrlRequest;
import io.github.bennixtech.daraja.config.DarajaProperties;
import io.github.bennixtech.daraja.error.DarajaResponseErrorHandler;
import io.github.bennixtech.daraja.exception.*;
import io.github.bennixtech.daraja.reversal.ReversalRequest;
import io.github.bennixtech.daraja.stkpush.StkPushRequest;
import io.github.bennixtech.daraja.transactionstatus.TransactionStatusRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;
import org.springframework.validation.beanvalidation.SpringConstraintValidatorFactory;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class DarajaClientIntegrationTest {
    private MockRestServiceServer tokenServer;
    private MockRestServiceServer apiServer;
    private DarajaApiClient client;
    private LocalValidatorFactoryBean validators;
    private DarajaProperties properties;
    private DarajaOAuth2Support oauth2;

    @BeforeEach
    void setUp() {
        var tokenBuilder = RestClient.builder();
        tokenServer = MockRestServiceServer.bindTo(tokenBuilder).build();
        properties = new DarajaProperties();
        properties.setConsumerKey("key");
        properties.setConsumerSecret("secret");
        oauth2 = new DarajaOAuth2Support(properties, tokenBuilder.build());

        var apiBuilder = RestClient.builder().baseUrl("https://sandbox.safaricom.co.ke")
                .requestInterceptor(oauth2.interceptor())
                .defaultStatusHandler(status -> status.isError(),
                        new DarajaResponseErrorHandler(JsonMapper.builder().build(), oauth2));
        apiServer = MockRestServiceServer.bindTo(apiBuilder).build();
        var beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("darajaProperties", properties);
        validators = new LocalValidatorFactoryBean();
        validators.setConstraintValidatorFactory(new SpringConstraintValidatorFactory(beanFactory));
        validators.afterPropertiesSet();
        client = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(apiBuilder.build()))
                .proxyFactoryCustomizer((factory, serviceType) -> factory.addAdvice(0,
                        new MethodValidationInterceptor(validators.getValidator())))
                .build().createClient(DarajaApiClient.class);
    }

    @AfterEach
    void verifyRequests() {
        try {
            tokenServer.verify();
            apiServer.verify();
        } finally {
            validators.close();
        }
    }

    private void token(String value, String lifetime) {
        tokenServer.expect(requestTo("https://sandbox.safaricom.co.ke/oauth/v1/generate?grant_type=client_credentials"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Basic a2V5OnNlY3JldA=="))
                .andExpect(headerDoesNotExist("Cookie"))
                .andRespond(withSuccess("{\"access_token\":\"" + value + "\",\"expires_in\":\"" + lifetime + "\"}",
                        MediaType.APPLICATION_JSON));
    }

    private void operation(String path, String expectedJson) {
        apiServer.expect(requestTo("https://sandbox.safaricom.co.ke" + path))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer first"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json(expectedJson, JsonCompareMode.LENIENT))
                .andRespond(withSuccess("{\"ResponseCode\":\"0\",\"ResponseDescription\":\"Accepted\","
                        + "\"CheckoutRequestID\":\"checkout-123\"}", MediaType.APPLICATION_JSON));
    }

    private StkPushRequest stk() {
        return StkPushRequest.builder().businessShortCode("174379").password("encoded")
                .timestamp("20261019133000").amount(1).phoneNumber("0712345678")
                .callBackURL("https://example.com/callback").accountReference("invoice-1")
                .transactionDesc("Invoice payment").build();
    }

    @Test
    void allSevenOperationsSerializeDarajaFieldsAndReuseApplicationToken() {
        token("first", "3600");
        operation("/mpesa/stkpush/v1/processrequest", """
                {"BusinessShortCode":"174379","Password":"encoded","Timestamp":"20261019133000",
                 "TransactionType":"CustomerPayBillOnline","Amount":"1","PartyA":"254712345678",
                 "PartyB":"174379","PhoneNumber":"254712345678","CallBackURL":"https://example.com/callback",
                 "AccountReference":"invoice-1","TransactionDesc":"Invoice payment"}
                """);
        operation("/mpesa/c2b/v1/registerurl", """
                {"ShortCode":"600000","ResponseType":"Completed","ConfirmationURL":"https://example.com/confirm",
                 "ValidationURL":"https://example.com/validate"}
                """);
        operation("/mpesa/b2c/v3/paymentrequest", """
                {"OriginatorConversationID":"merchant-payment-1",
                 "InitiatorName":"initiator","SecurityCredential":"encrypted","CommandID":"BusinessPayment",
                 "Amount":"10","PartyA":"600000","PartyB":"254712345678","Remarks":"Payment",
                 "QueueTimeOutURL":"https://example.com/timeout","ResultURL":"https://example.com/result","Occassion":"salary"}
                """);
        operation("/mpesa/b2b/v1/paymentrequest", """
                {"Initiator":"initiator","SecurityCredential":"encrypted","CommandID":"BusinessPayBill",
                 "SenderIdentifierType":"4","RecieverIdentifierType":"4","Amount":"10","PartyA":"600000",
                 "PartyB":"600001","Remarks":"Payment","QueueTimeOutURL":"https://example.com/timeout",
                 "ResultURL":"https://example.com/result","AccountReference":"invoice-1"}
                """);
        operation("/mpesa/transactionstatus/v1/query", """
                {"Initiator":"initiator","SecurityCredential":"encrypted","CommandID":"TransactionStatusQuery",
                 "TransactionID":"ABC123","PartyA":"600000","IdentifierType":"4","ResultURL":"https://example.com/result",
                 "QueueTimeOutURL":"https://example.com/timeout","Remarks":"Status","Occasion":"audit"}
                """);
        operation("/mpesa/accountbalance/v1/query", """
                {"Initiator":"initiator","SecurityCredential":"encrypted","CommandID":"AccountBalance",
                 "PartyA":"600000","IdentifierType":"4","Remarks":"Balance","QueueTimeOutURL":"https://example.com/timeout",
                 "ResultURL":"https://example.com/result"}
                """);
        operation("/mpesa/reversal/v1/request", """
                {"Initiator":"initiator","SecurityCredential":"encrypted","CommandID":"TransactionReversal",
                 "TransactionID":"ABC123","Amount":"10","ReceiverParty":"600000","RecieverIdentifierType":"11",
                 "ResultURL":"https://example.com/result","QueueTimeOutURL":"https://example.com/timeout",
                 "Remarks":"Refund","Occasion":"refund"}
                """);

        assertThat(client.initiateStkPush(stk()).checkoutRequestID()).isEqualTo("checkout-123");
        assertThat(client.registerC2BUrls(C2BRegisterUrlRequest.builder().shortCode("600000").responseType("Completed")
                .confirmationURL("https://example.com/confirm").validationURL("https://example.com/validate")
                .build()).responseDescription()).isEqualTo("Accepted");
        assertThat(client.b2cPayment(B2CRequest.builder().originatorConversationID("merchant-payment-1")
                .initiatorName("initiator").securityCredential("encrypted")
                .commandID("BusinessPayment").amount("10").partyA("600000").partyB("254712345678").remarks("Payment")
                .queueTimeOutURL("https://example.com/timeout").resultURL("https://example.com/result")
                .occassion("salary").build()).responseCode()).isEqualTo("0");
        assertThat(client.b2bPayment(B2BRequest.builder().initiator("initiator").securityCredential("encrypted")
                .commandID("BusinessPayBill").senderIdentifierType("4").recieverIdentifierType("4").amount("10")
                .partyA("600000").partyB("600001").remarks("Payment").queueTimeOutURL("https://example.com/timeout")
                .resultURL("https://example.com/result").accountReference("invoice-1").build()).responseCode()).isEqualTo("0");
        assertThat(client.transactionStatus(TransactionStatusRequest.builder().initiator("initiator")
                .securityCredential("encrypted").commandID("TransactionStatusQuery").transactionID("ABC123")
                .partyA("600000").identifierType("4").resultURL("https://example.com/result")
                .queueTimeOutURL("https://example.com/timeout").remarks("Status").occasion("audit")
                .build()).responseCode()).isEqualTo("0");
        assertThat(client.accountBalance(AccountBalanceRequest.builder().initiator("initiator")
                .securityCredential("encrypted").commandID("AccountBalance").partyA("600000").identifierType("4")
                .remarks("Balance").queueTimeOutURL("https://example.com/timeout").resultURL("https://example.com/result")
                .build()).responseCode()).isEqualTo("0");
        assertThat(client.reversal(ReversalRequest.builder().initiator("initiator").securityCredential("encrypted")
                .commandID("TransactionReversal").transactionID("ABC123").amount("10").receiverParty("600000")
                .recieverIdentifierType("11").resultURL("https://example.com/result")
                .queueTimeOutURL("https://example.com/timeout").remarks("Refund").occasion("refund")
                .build()).responseCode()).isEqualTo("0");
    }

    @Test
    void renewsTokenInsideExpirySkewWithoutUserOrServletContext() {
        token("short-lived", "1");
        token("renewed", "3600");
        apiServer.expect(anything()).andExpect(header("Authorization", "Bearer short-lived"))
                .andRespond(withSuccess("{\"ResponseCode\":\"0\"}", MediaType.APPLICATION_JSON));
        apiServer.expect(anything()).andExpect(header("Authorization", "Bearer renewed"))
                .andRespond(withSuccess("{\"ResponseCode\":\"0\"}", MediaType.APPLICATION_JSON));
        client.initiateStkPush(stk());
        client.initiateStkPush(stk());
    }

    @Test
    void unauthorizedInvalidatesTokenForNextCallWithoutReplayingPayment() {
        token("first", "3600");
        token("second", "3600");
        apiServer.expect(anything()).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .body("{\"errorCode\":\"invalid_token\",\"errorMessage\":\"Expired\"}")
                .contentType(MediaType.APPLICATION_JSON));
        apiServer.expect(anything()).andExpect(header("Authorization", "Bearer second"))
                .andRespond(withSuccess("{\"ResponseCode\":\"0\"}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.initiateStkPush(stk()))
                .isInstanceOfSatisfying(DarajaAuthenticationException.class, ex -> {
                    assertThat(ex.getHttpStatus()).isEqualTo(401);
                    assertThat(ex.getErrorCode()).isEqualTo("invalid_token");
                });
        client.initiateStkPush(stk());
    }

    @Test
    void invalidTokenResponseStopsPaymentRequest() {
        token("", "3600");
        assertThatThrownBy(() -> client.initiateStkPush(stk()))
                .isInstanceOfSatisfying(OAuth2AuthorizationException.class,
                        ex -> assertThat(ex.getError().getErrorCode()).isEqualTo("invalid_token_response"));
    }

    @Test
    void acceptsDocumentedNumericExpiryWithoutTokenType() {
        tokenServer.expect(requestTo("https://sandbox.safaricom.co.ke/oauth/v1/generate?grant_type=client_credentials"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Basic a2V5OnNlY3JldA=="))
                .andRespond(withSuccess("{\"access_token\":\"first\",\"expires_in\":3599}",
                        MediaType.APPLICATION_JSON));
        operation("/mpesa/stkpush/v1/processrequest", "{\"BusinessShortCode\":\"174379\"}");
        assertThat(client.initiateStkPush(stk()).responseCode()).isEqualTo("0");
    }

    @Test
    void nonJsonServerErrorPreservesStatusAndBody() {
        token("first", "3600");
        apiServer.expect(anything()).andRespond(withStatus(HttpStatus.BAD_GATEWAY).body("gateway unavailable"));
        assertThatThrownBy(() -> client.initiateStkPush(stk()))
                .isInstanceOfSatisfying(DarajaApiException.class, ex -> {
                    assertThat(ex.getHttpStatus()).isEqualTo(502);
                    assertThat(ex.getResponseBody()).isEqualTo("gateway unavailable");
                });
    }

    @Test
    void badRequestMapsToValidationException() {
        token("first", "3600");
        apiServer.expect(anything()).andRespond(withBadRequest()
                .body("{\"errorCode\":\"bad_amount\",\"errorMessage\":\"Invalid amount\"}")
                .contentType(MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.initiateStkPush(stk()))
                .isInstanceOfSatisfying(InvalidDarajaRequestException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("bad_amount"));
    }

    @Test
    void validationRejectsInvalidPayloadBeforeAuthenticationOrHttp() {
        assertThatThrownBy(() -> client.initiateStkPush(stk().toBuilder().amount("-1").build()))
                .isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> client.initiateStkPush(stk().toBuilder().callBackURL("https://localhost/callback").build()))
                .isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> client.initiateStkPush(null)).isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void productionCallbackConstraintRejectsHttpAndRootPathsBeforeHttp() {
        properties.setEnvironment(DarajaEnvironment.PRODUCTION);
        assertThatThrownBy(() -> client.initiateStkPush(stk().toBuilder()
                .callBackURL("http://example.com/callback").build())).isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> client.initiateStkPush(stk().toBuilder()
                .callBackURL("https://example.com").build())).isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void callbackConstraintsApplyToOtherOperations() {
        var request = C2BRegisterUrlRequest.builder().shortCode("600000").responseType("Completed")
                .confirmationURL("https://example.com/confirm").validationURL("https://localhost/validate").build();
        assertThatThrownBy(() -> client.registerC2BUrls(request))
                .isInstanceOfSatisfying(ConstraintViolationException.class, ex ->
                        assertThat(ex.getConstraintViolations()).anySatisfy(violation ->
                                assertThat(violation.getPropertyPath().toString()).endsWith("validationURL")));
    }

    @Test
    void emptyResponseUsesStandardReturnValueValidation() {
        token("first", "3600");
        apiServer.expect(anything()).andRespond(withNoContent());
        assertThatThrownBy(() -> client.initiateStkPush(stk())).isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void b2cRequiresMerchantConversationIdBeforeAnyHttpCall() {
        var request = B2CRequest.builder().initiatorName("initiator").securityCredential("encrypted")
                .commandID("BusinessPayment").amount("10").partyA("600000").partyB("254712345678")
                .remarks("Payment").queueTimeOutURL("https://example.com/timeout")
                .resultURL("https://example.com/result").build();
        assertThatThrownBy(() -> client.b2cPayment(request))
                .isInstanceOfSatisfying(ConstraintViolationException.class, ex ->
                        assertThat(ex.getConstraintViolations()).anySatisfy(violation ->
                                assertThat(violation.getPropertyPath().toString()).endsWith("originatorConversationID")));
    }

    @Test
    void transportErrorsRemainSpringExceptionsAndAreNotRetried() {
        token("first", "3600");
        apiServer.expect(anything()).andRespond(withException(new IOException("connection lost")));
        assertThatThrownBy(() -> client.initiateStkPush(stk())).isInstanceOf(ResourceAccessException.class);
    }
}
