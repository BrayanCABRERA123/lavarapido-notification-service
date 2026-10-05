package com.lavarapido.notification.infrastructure.adapter.out.identity;

import com.lavarapido.notification.domain.port.out.UserContactDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Optional;

/**
 * Pide el contacto a security-service: GET /internal/v1/users/{id}/contact con la llave interna
 * (X-Internal-Key). Sin llave configurada no llama a nadie y las notificaciones van sin correo.
 */
@Component
class SecurityServiceUserContactDirectory implements UserContactDirectory {

    private static final Logger log = LoggerFactory.getLogger(SecurityServiceUserContactDirectory.class);
    private static final String HEADER = "X-Internal-Key";

    private final RestClient client;
    private final String apiKey;

    SecurityServiceUserContactDirectory(@Value("${app.security-service.base-url}") String baseUrl,
                                        @Value("${app.security-service.timeout:3s}") Duration timeout,
                                        @Value("${app.internal.api-key:}") String apiKey) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.apiKey = apiKey == null ? "" : apiKey.strip();
        if (this.apiKey.isEmpty()) {
            log.warn("INTERNAL_API_KEY is not set: booking emails and reminders will not be emailed");
        }
    }

    @Override
    public Optional<UserContact> contactOf(long userId) {
        if (apiKey.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(client.get()
                    .uri("/internal/v1/users/{id}/contact", userId)
                    .header(HEADER, apiKey)
                    .retrieve()
                    .body(UserContact.class));
        } catch (RestClientException e) {
            log.warn("Could not read the contact of user {} from security-service: {}", userId, e.getMessage());
            return Optional.empty();
        }
    }
}
