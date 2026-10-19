package io.github.bennixtech.daraja.c2b;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import io.github.bennixtech.daraja.validation.CallbackUrl;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMin;

@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public record C2BRegisterUrlRequest(
    @NotBlank @JsonProperty("ShortCode")
    String shortCode,

    @NotBlank @JsonProperty("ResponseType")
    String responseType,

    @NotBlank @CallbackUrl @JsonProperty("ConfirmationURL")
    String confirmationURL,

    @NotBlank @CallbackUrl @JsonProperty("ValidationURL")
    String validationURL
) {
}
