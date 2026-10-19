package io.github.bennixtech.daraja.stkpush;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;
import io.github.bennixtech.daraja.validation.CallbackUrl;

/** STK initiation payload. The canonical constructor also applies builder defaults and phone normalization. */
@Builder(toBuilder = true)
public record StkPushRequest(
        @NotBlank @JsonProperty("BusinessShortCode") String businessShortCode,
        @NotBlank @JsonProperty("Password") String password,
        @NotBlank @Pattern(regexp = "[0-9]{14}") @JsonProperty("Timestamp") String timestamp,
        @NotBlank @JsonProperty("TransactionType") String transactionType,
        @NotBlank @DecimalMin("1") @JsonProperty("Amount") String amount,
        @NotBlank @Pattern(regexp = "254[0-9]{9}") @JsonProperty("PartyA") String partyA,
        @NotBlank @JsonProperty("PartyB") String partyB,
        @NotBlank @Pattern(regexp = "254[0-9]{9}") @JsonProperty("PhoneNumber") String phoneNumber,
        @NotBlank @CallbackUrl @JsonProperty("CallBackURL") String callBackURL,
        @NotBlank @JsonProperty("AccountReference") String accountReference,
        @NotBlank @JsonProperty("TransactionDesc") String transactionDesc) {

    public StkPushRequest {
        phoneNumber = normalizePhoneNumber(phoneNumber);
        partyA = partyA == null ? phoneNumber : normalizePhoneNumber(partyA);
        partyB = partyB == null ? businessShortCode : partyB;
        transactionType = transactionType == null ? "CustomerPayBillOnline" : transactionType;
    }

    private static String normalizePhoneNumber(String phone) {
        if (phone == null) return null;
        String value = phone.replaceAll("[\\s()+-]", "");
        if (value.matches("0[0-9]{9}")) return "254" + value.substring(1);
        if (value.matches("[17][0-9]{8}")) return "254" + value;
        return value;
    }

    public static class StkPushRequestBuilder {
        private String amount;

        public StkPushRequestBuilder amount(String amount) {
            this.amount = amount;
            return this;
        }

        public StkPushRequestBuilder amount(long amount) {
            this.amount = String.valueOf(amount);
            return this;
        }
    }

    @Override
    public String toString() {
        return "StkPushRequest[businessShortCode=" + businessShortCode + ", password=[REDACTED]]";
    }
}
