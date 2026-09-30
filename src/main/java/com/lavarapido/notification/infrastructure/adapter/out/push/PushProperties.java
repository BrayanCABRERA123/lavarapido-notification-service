package com.lavarapido.notification.infrastructure.adapter.out.push;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuración del envío de push (ADR-011).
 *
 * @param enabled     false = las push solo se escriben en el log (desarrollo sin celular)
 * @param expoUrl     API de push de Expo
 * @param accessToken token de acceso de la cuenta de Expo; opcional, solo si se activa
 *                    "Enhanced security for push" en expo.dev. Nunca se sube al repositorio
 * @param timeout     tiempo máximo de espera de la respuesta de Expo
 */
@ConfigurationProperties(prefix = "app.push")
public record PushProperties(boolean enabled, String expoUrl, String accessToken, Duration timeout) {

    public PushProperties {
        expoUrl = expoUrl == null || expoUrl.isBlank() ? "https://exp.host/--/api/v2/push/send" : expoUrl;
        timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
    }

    public boolean hasAccessToken() {
        return accessToken != null && !accessToken.isBlank();
    }
}
