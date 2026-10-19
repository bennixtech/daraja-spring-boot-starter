package io.github.bennixtech.daraja.reversal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import io.github.bennixtech.daraja.validation.CallbackUrl;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMin;

@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReversalRequest(
    @NotBlank @JsonProperty("Initiator")
    String initiator,

    @NotBlank @JsonProperty("SecurityCredential")
    String securityCredential,

    @NotBlank @JsonProperty("CommandID")
    String commandID,

    @NotBlank @JsonProperty("TransactionID")
    String transactionID,

    @NotBlank @DecimalMin("1") @JsonProperty("Amount")
    String amount,

    @NotBlank @JsonProperty("ReceiverParty")
    String receiverParty,

    @NotBlank @JsonProperty("RecieverIdentifierType")
    String recieverIdentifierType,

    @NotBlank @CallbackUrl @JsonProperty("ResultURL")
    String resultURL,

    @NotBlank @CallbackUrl @JsonProperty("QueueTimeOutURL")
    String queueTimeOutURL,

    @NotBlank @JsonProperty("Remarks")
    String remarks,

    @JsonProperty("Occasion")
    String occasion
) {
    @Override
    public String toString() { return "ReversalRequest[securityCredential=[REDACTED]]"; }
}
