package io.github.bennixtech.daraja;

import io.github.bennixtech.daraja.stkpush.StkPushCallback;
import io.github.bennixtech.daraja.util.CallbackUrlValidator;
import io.github.bennixtech.daraja.util.StkPushPasswordUtil;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.*;

class CallbackAndUtilitiesTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void callbackSupportsNumericPhoneAndAmountAndIgnoresUnknownFields() {
        var callback = mapper.readValue("""
                {"Body":{"stkCallback":{"MerchantRequestID":"m","CheckoutRequestID":"c","ResultCode":0,
                  "ResultDesc":"Success","extra":"ignored","CallbackMetadata":{"Item":[
                    {"Name":"Amount","Value":10.50},{"Name":"PhoneNumber","Value":254712345678},
                    {"Name":"MpesaReceiptNumber","Value":"ABC123"},{"Name":"Balance"}]}}}}
                """, StkPushCallback.class);
        assertThat(callback.isSuccessful()).isTrue();
        var metadata = callback.body().stkCallback().callbackMetadata();
        assertThat(metadata.getAmount()).isEqualByComparingTo("10.50");
        assertThat(metadata.getPhoneNumber()).isEqualTo("254712345678");
        assertThat(metadata.getReceiptNumber()).isEqualTo("ABC123");
        assertThat(metadata.getItemValue("Balance")).isNull();
        assertThat(callback.toBuilder().build()).isEqualTo(callback);
    }

    @Test
    void missingResultCodeCannotBecomeSuccessfulPayment() {
        var callback = mapper.readValue("""
                {"Body":{"stkCallback":{"CheckoutRequestID":"c","ResultDesc":"Incomplete"}}}
                """, StkPushCallback.class);
        assertThat(callback.isSuccessful()).isFalse();
        var cancelled = StkPushCallback.StkCallback.builder().resultCode(1032).build();
        assertThat(cancelled.wasCancelled()).isTrue();
        assertThat(cancelled.callbackMetadata()).isNull();
    }

    @Test
    void passwordUsesNairobiTimeAndDoesNotLeakFromToString() {
        var result = StkPushPasswordUtil.generatePassword("174379", "passkey",
                Clock.fixed(Instant.parse("2026-10-19T10:00:00Z"), ZoneOffset.UTC));
        assertThat(result.timestamp()).isEqualTo("20261019130000");
        assertThat(result.password()).isEqualTo("MTc0Mzc5cGFzc2tleTIwMjYxMDE5MTMwMDAw");
        assertThat(result.toString()).doesNotContain(result.password());
        assertThatThrownBy(() -> StkPushPasswordUtil.generatePassword("174379", "passkey", "20260230120000"))
                .isInstanceOf(java.time.DateTimeException.class);
    }

    @Test
    void callbackChecksRejectPrivateHostsAndEnforceProductionHttps() {
        for (String url : new String[]{"https://localhost/cb", "https://10.0.0.1/cb",
                "https://[::1]/cb", "https://192.168.1.1/cb", "https://user:pass@example.com/cb"}) {
            assertThat(CallbackUrlValidator.validate(url).isValid()).as(url).isFalse();
        }
        assertThat(CallbackUrlValidator.validate("https://example.com/cb").isValid()).isTrue();
        assertThat(CallbackUrlValidator.validateForProduction("http://example.com/cb").isValid()).isFalse();
        assertThat(CallbackUrlValidator.validateForProduction("https://example.com").isValid()).isFalse();
    }
}
