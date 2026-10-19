# Daraja Spring Boot Starter

A blocking Safaricom Daraja client for **Spring Boot 4.1.1 and Java 25**.
The public API is a Spring **HTTP Service interface**: `@HttpExchange` and
`@PostExchange` declare the requests, and `HttpServiceProxyFactory` creates a
proxy backed by `RestClientAdapter`. Token acquisition also uses an HTTP Service.

## Build and use

Run with JDK 25 and Maven 3.9+:

```shell
mvn verify
mvn install
```

This produces a normal library JAR, not an executable Spring Boot application.
After installing locally, add it to your Spring Boot application's POM:

```xml
<dependency>
    <groupId>io.github.bennixtech</groupId>
    <artifactId>daraja-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

The coordinates above are for the local build; this project has not been published
to Maven Central. Lombok is an optional build dependency of this library.
Consumers can call the generated builders without installing Lombok.

## Configuration

```yaml
daraja:
  enabled: true
  environment: SANDBOX
  consumer-key: ${DARAJA_CONSUMER_KEY}
  consumer-secret: ${DARAJA_CONSUMER_SECRET}
  connect-timeout: 10s
  read-timeout: 60s
```

Use `PRODUCTION` for live transactions. The default is `SANDBOX`.
Credentials and positive timeouts are validated at startup. Set
`daraja.enabled=false` to disable the integration without supplying credentials.
An optional `daraja.base-url` overrides the environment URL for a gateway or tests;
the environment still controls callback validation.

Auto-configuration registers a `DarajaApiClient` and a named `darajaRestClient`.
An application-provided `DarajaApiClient` makes the entire integration back off.
You can supply your own named `darajaRestClient` or a `DarajaOAuth2Support` bean
for advanced customization. Replacing the RestClient makes its authentication and
HTTP configuration your responsibility.

The starter clones Boot's configured `RestClient.Builder` for each transport,
retaining its converters and customizers. It selects the JDK HTTP client with
the Daraja-specific connect/read timeouts and disables redirects.
Boot uses Jackson 3; DTO annotations remain in `com.fasterxml.jackson.annotation`.

## Initiate an STK payment

```java
import io.github.bennixtech.daraja.DarajaApiClient;
import io.github.bennixtech.daraja.stkpush.StkPushRequest;
import io.github.bennixtech.daraja.stkpush.StkPushResponse;
import io.github.bennixtech.daraja.util.StkPushPasswordUtil;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {
    private final DarajaApiClient daraja;

    public PaymentService(DarajaApiClient daraja) {
        this.daraja = daraja;
    }

    public StkPushResponse requestPayment(String shortCode, String passkey,
                                          String phone, long amount, String invoice) {
        var password = StkPushPasswordUtil.generatePassword(shortCode, passkey);
        return daraja.initiateStkPush(StkPushRequest.builder()
                .businessShortCode(shortCode)
                .password(password.password())
                .timestamp(password.timestamp())
                .amount(amount)
                .phoneNumber(phone)
                .callBackURL("https://your-domain.example/payments/stk/callback")
                .accountReference(invoice)
                .transactionDesc("Invoice payment")
                .build());
    }
}
```

STK defaults: `CustomerPayBillOnline`, PartyB equal to the shortcode, and PartyA
equal to the normalized phone number. Local phone numbers such as `0712345678`
and `+254712345678` become `254712345678`. You can explicitly override PartyA,
PartyB, and transactionType. Password timestamps use Africa/Nairobi independently
of the machine timezone.

Every request, response, and nested callback DTO is a Java record with Lombok
`@Builder(toBuilder = true)`. Input validation occurs when the client method is
called, before any token or API request. Required fields, positive amounts, STK
phone formats, and callback URL structure are checked. Production callback URLs
require HTTPS and a specific path. URL validation does not perform DNS resolution
or prove that Safaricom can reach your server.

## Operations retained from the reference

| Method | Request |
| --- | --- |
| `initiateStkPush` | `StkPushRequest` |
| `registerC2BUrls` | `C2BRegisterUrlRequest` |
| `b2cPayment` | `B2CRequest` |
| `b2bPayment` | `B2BRequest` |
| `transactionStatus` | `TransactionStatusRequest` |
| `accountBalance` | `AccountBalanceRequest` |
| `reversal` | `ReversalRequest` |

These retain the reference SDK's operations and JSON field names, including the
API spellings `RecieverIdentifierType` and `Occassion`. B2C uses the current
documented `/mpesa/b2c/v3/paymentrequest` endpoint and requires an explicit
`originatorConversationID` on `B2CRequest.builder()`. Generate and persist a unique
merchant reference for each payment; do not generate a new reference blindly
when an earlier transaction's outcome is unknown.

Protocol references:
[Authorization](https://developer.safaricom.co.ke/apis/Authorization),
[B2C](https://developer.safaricom.co.ke/apis/BusinessToCustomer),
[C2B](https://developer.safaricom.co.ke/apis/CustomerToBusiness),
[STK Push](https://developer.safaricom.co.ke/apis/MpesaExpressSimulate),
[Transaction Status](https://developer.safaricom.co.ke/apis/TransactionStatus).

Verify endpoint availability and required credentials for your Safaricom account
before production use. The existing environment URL helpers are retained, but
STK query and C2B simulation are not implemented operations.

For B2C, B2B, balance, status, and reversal, supply the encrypted
`SecurityCredential` yourself. Certificate-based credential generation is not
provided by this starter.

## OAuth2

Daraja's token protocol is adapted to Spring Security:

1. A separate `DarajaAuthApiClient` HTTP Service sends a GET request to
   `/oauth/v1/generate?grant_type=client_credentials` with Basic authentication.
2. `DarajaTokenResponseClient` converts the response to Spring's
   `OAuth2AccessTokenResponse`.
3. `AuthorizedClientServiceOAuth2AuthorizedClientManager` stores/reuses the token
   and obtains a replacement when it expires or falls within Spring's expiry skew.
4. `OAuth2ClientHttpRequestInterceptor` adds the bearer token to payment requests.

Tokens are scoped to this application's Daraja client, not the current logged-in
user. Scheduled tasks and background calls work without a servlet request or
security context. Concurrent authorization attempts are serialized to avoid
duplicate token acquisition. Tokens are stored in memory per application instance.
If multiple instances share consumer credentials, coordinate token acquisition:
the official Authorization FAQ says generating a new token invalidates the previous
one. The starter's in-process lock does not coordinate separate application instances.

Daraja's registrations, authorized-client service, and manager are isolated inside
`DarajaOAuth2Support`; they do not replace the application's OAuth2 client beans.
The starter does not define an inbound security filter chain or login flow.

## Callbacks

The initial API response acknowledges a request; it does not establish that a
payment completed. Your application's callback endpoint must handle the outcome,
correlate the checkout/conversation identifiers, and process duplicate callbacks
idempotently.

For an application that already includes Spring MVC:

```java
import io.github.bennixtech.daraja.stkpush.StkPushCallback;
import io.github.bennixtech.daraja.webhook.StkPushCallbackHandler;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payments/stk")
public class StkCallbackController {
    private final StkPushCallbackHandler handler;

    public StkCallbackController(StkPushCallbackHandler handler) {
        this.handler = handler;
    }

    @PostMapping("/callback")
    public void callback(@RequestBody StkPushCallback callback) {
        handler.onCallbackReceived(callback);
        if (callback.isSuccessful()) {
            handler.onPaymentSuccess(callback);
        } else {
            handler.onPaymentFailure(callback);
        }
    }
}
```

Implement `StkPushCallbackHandler` as an application bean. No controller or
automatic callback dispatch is installed by this starter. Callback metadata
provides receipt, numeric-phone conversion, and a `BigDecimal` amount.
Failed callbacks may have no metadata. A missing result code never implies success.

## Errors

- `ConstraintViolationException`: invalid request parameters/DTO fields or an empty response.
- `OAuth2AuthorizationException`: token acquisition errors.
- `RestClientException` (including `ResourceAccessException`): transport or response conversion failures.
- `InvalidDarajaRequestException`: applicable Daraja API 4xx errors.
- `DarajaAuthenticationException`: Daraja API 401/403 responses.
- `DarajaApiException`: other Daraja HTTP errors, including rate limits and server errors.

Daraja exceptions expose `getErrorCode()` and `getHttpStatus()` when available.
`DarajaApiException` also retains the raw response body for HTTP errors.
Token endpoint errors use a sanitized message.

Validation uses Jakarta `@Valid` and `@NotNull` on the HTTP Service methods,
the standard DTO constraints, and a `@CallbackUrl` constraint on each callback URL.
Spring's provided `MethodValidationInterceptor` is installed before the HTTP
Service's request advice, so validation happens before authentication or HTTP.
No custom client invocation interceptor is used. The callback constraint validator
is created by Spring and uses `DarajaProperties` to select the production policy.
Spring token/transport exceptions propagate without an additional translation layer.

An API 401 clears the stored token for the next call. The starter does **not**
automatically replay payment requests. On transport failure, the transaction
outcome may be unknown; correlate callbacks or query transaction status before
deciding whether to retry.

## Tests

Tests use Spring's mock HTTP server and application-context runner; no real
credentials, network payments, or Safaricom sandbox are required. They cover
all seven declarative operations, JSON mapping, token reuse/renewal, token rejection,
input validation, callback parsing, auto-configuration, and application overrides.

## Attribution

Models, endpoint definitions, exception types, and callback conventions were adapted
from [Wilfred Mwangi Njuguna's Daraja Java SDK](https://github.com/wmnjuguna/daraja-java-sdk).
The original MIT license is retained in LICENSE and included in the library JAR.
