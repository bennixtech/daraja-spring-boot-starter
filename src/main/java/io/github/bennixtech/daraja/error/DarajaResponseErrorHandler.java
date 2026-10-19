package io.github.bennixtech.daraja.error;

import io.github.bennixtech.daraja.auth.DarajaOAuth2Support;
import io.github.bennixtech.daraja.exception.DarajaApiException;
import io.github.bennixtech.daraja.exception.DarajaAuthenticationException;
import io.github.bennixtech.daraja.exception.InvalidDarajaRequestException;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class DarajaResponseErrorHandler implements RestClient.ResponseSpec.ErrorHandler {
    private final JsonMapper mapper;
    private final DarajaOAuth2Support oauth2;

    public DarajaResponseErrorHandler(JsonMapper mapper, DarajaOAuth2Support oauth2) {
        this.mapper = mapper;
        this.oauth2 = oauth2;
    }

    @Override
    public void handle(HttpRequest request, ClientHttpResponse response) throws IOException {
        int status = response.getStatusCode().value();
        String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
        String code = null;
        String message = "Daraja API request failed";
        try {
            JsonNode root = mapper.readTree(body);
            code = field(root, "errorCode", "error_code", "code", "ResponseCode", "responseCode");
            String description = field(root, "errorMessage", "error_message", "message",
                    "ResponseDescription", "responseDescription", "ResultDesc", "resultDesc");
            if (description != null) {
                message = description;
            }
        } catch (RuntimeException ignored) {
            // Non-JSON errors still retain the status and raw body on DarajaApiException.
        }
        String detail = "HTTP " + status + ": " + message;
        if (status == 401) {
            oauth2.clearToken();
        }
        if (status == 401 || status == 403) {
            throw new DarajaAuthenticationException(detail, code, status);
        }
        if (status >= 400 && status < 500 && status != 404 && status != 429) {
            throw new InvalidDarajaRequestException(detail, code, status);
        }
        throw new DarajaApiException(detail, body, code, status);
    }

    private String field(JsonNode root, String... names) {
        if (root == null) {
            return null;
        }
        for (String name : names) {
            JsonNode node = root.get(name);
            if (node != null && !node.isNull() && !node.asString().isBlank()) {
                return node.asString();
            }
        }
        return null;
    }
}
