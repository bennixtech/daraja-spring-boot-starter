package io.github.bennixtech.daraja.accountbalance;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import io.github.bennixtech.daraja.validation.CallbackUrl;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMin;

@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountBalanceRequest(
    @NotBlank @JsonProperty("Initiator")
    String initiator,

    @NotBlank @JsonProperty("SecurityCredential")
    String securityCredential,

    @NotBlank @JsonProperty("CommandID")
    String commandID,

    @NotBlank @JsonProperty("PartyA")
    String partyA,

    @NotBlank @JsonProperty("IdentifierType")
    String identifierType,

    @NotBlank @JsonProperty("Remarks")
    String remarks,

    @NotBlank @CallbackUrl @JsonProperty("QueueTimeOutURL")
    String queueTimeOutURL,

    @NotBlank @CallbackUrl @JsonProperty("ResultURL")
    String resultURL
) {
    @Override
    public String toString() { return "AccountBalanceRequest[securityCredential=[REDACTED]]"; }
}
