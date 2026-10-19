package io.github.bennixtech.daraja.b2b;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import io.github.bennixtech.daraja.validation.CallbackUrl;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMin;

@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public record B2BRequest(
    @NotBlank @JsonProperty("Initiator")
    String initiator,

    @NotBlank @JsonProperty("SecurityCredential")
    String securityCredential,

    @NotBlank @JsonProperty("CommandID")
    String commandID,

    @NotBlank @JsonProperty("SenderIdentifierType")
    String senderIdentifierType,

    @NotBlank @JsonProperty("RecieverIdentifierType")
    String recieverIdentifierType,

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

    @NotBlank @JsonProperty("AccountReference")
    String accountReference
) {
    @Override
    public String toString() { return "B2BRequest[securityCredential=[REDACTED]]"; }
}
