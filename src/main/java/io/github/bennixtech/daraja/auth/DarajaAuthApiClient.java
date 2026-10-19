package io.github.bennixtech.daraja.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;

import java.net.URI;

/** Separate HTTP Service for token acquisition, without the bearer-token interceptor. */
public interface DarajaAuthApiClient {
    @GetExchange(accept = MediaType.APPLICATION_JSON_VALUE)
    DarajaTokenResponseClient.TokenResponse generateAccessToken(
            URI tokenUri, @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);
}
