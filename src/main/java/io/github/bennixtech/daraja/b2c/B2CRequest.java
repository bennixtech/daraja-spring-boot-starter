package io.github.bennixtech.daraja.b2c;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import io.github.bennixtech.daraja.validation.CallbackUrl;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMin;

@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public record B2CRequest(
    @NotBlank @JsonProperty("OriginatorConversationID")
    String originatorConversationID,

    @NotBlank @JsonProperty("InitiatorName")
    String initiatorName,

    @NotBlank @JsonProperty("SecurityCredential")
    String securityCredential,

    @NotBlank @JsonProperty("CommandID")
    String commandID,

    @NotBlank @DecimalMin("1") @JsonProperty("Amount")
    String amount,

    @NotBlank @JsonProperty("PartyA")
    String partyA,

    @NotBlank @JsonProperty("PartyB")
    String partyB,

    @NotBlank @JsonProperty("Remarks")
    String remarks,

    @NotBlank @CallbackUrl @JsonProperty("QueueTimeOutURL")
    String queueTimeOutURL,

    @NotBlank @CallbackUrl @JsonProperty("ResultURL")
    String resultURL,

    @JsonProperty("Occassion")
    String occassion
) {
    @Override
    public String toString() { return "B2CRequest[securityCredential=[REDACTED]]"; }
}
