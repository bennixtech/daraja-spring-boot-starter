package io.github.bennixtech.daraja.stkpush;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public record StkPushCallback(@JsonProperty("Body") CallbackBody body) {

    @Builder(toBuilder = true)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CallbackBody(@JsonProperty("stkCallback") StkCallback stkCallback) {}

    @Builder(toBuilder = true)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StkCallback(
            @JsonProperty("MerchantRequestID") String merchantRequestID,
            @JsonProperty("CheckoutRequestID") String checkoutRequestID,
            @JsonProperty("ResultCode") Integer resultCode,
            @JsonProperty("ResultDesc") String resultDesc,
            @JsonProperty("CallbackMetadata") CallbackMetadata callbackMetadata) {
        public boolean isSuccessful() { return Integer.valueOf(0).equals(resultCode); }
        public boolean wasCancelled() { return Integer.valueOf(1032).equals(resultCode); }
        public boolean wasTimedOut() { return Integer.valueOf(1037).equals(resultCode); }
    }

    @Builder(toBuilder = true)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CallbackMetadata(@JsonProperty("Item") List<CallbackItem> items) {
        public CallbackMetadata {
            items = items == null ? List.of() : List.copyOf(items);
        }

        public Object getItemValue(String name) {
            for (CallbackItem item : items) {
                if (name.equals(item.name())) return item.value();
            }
            return null;
        }

        public BigDecimal getAmount() {
            Object value = getItemValue("Amount");
            return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
        }

        public String getReceiptNumber() { return stringValue("MpesaReceiptNumber"); }
        public String getPhoneNumber() { return stringValue("PhoneNumber"); }

        public long getTransactionDate() {
            String value = stringValue("TransactionDate");
            return value == null ? 0L : Long.parseLong(value);
        }

        private String stringValue(String name) {
            Object value = getItemValue(name);
            return value == null ? null : value.toString();
        }
    }

    @Builder(toBuilder = true)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CallbackItem(@JsonProperty("Name") String name, @JsonProperty("Value") Object value) {}

    public boolean isSuccessful() {
        return body != null && body.stkCallback() != null && body.stkCallback().isSuccessful();
    }

    public String getResultDescription() {
        return body == null || body.stkCallback() == null ? null : body.stkCallback().resultDesc();
    }

    public String getMerchantRequestID() {
        return body == null || body.stkCallback() == null ? null : body.stkCallback().merchantRequestID();
    }

    public String getCheckoutRequestID() {
        return body == null || body.stkCallback() == null ? null : body.stkCallback().checkoutRequestID();
    }
}
