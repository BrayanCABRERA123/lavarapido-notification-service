package com.lavarapido.notification.infrastructure.adapter.in.web.dto;

import com.lavarapido.notification.domain.model.DeviceToken;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Cuerpos y respuestas de los endpoints de celulares (push). */
public final class DeviceRequests {

    private DeviceRequests() {
    }

    /** Lo manda la app al iniciar sesión: el token de Expo y si es Android o iOS. */
    public record RegisterDeviceRequest(
            @NotBlank(message = "token is required") @Size(max = 200, message = "token is too long") String token,
            @NotBlank(message = "platform is required") String platform) {
    }

    /** Lo manda la app al cerrar sesión, para dejar de recibir push en ese celular. */
    public record UnregisterDeviceRequest(
            @NotBlank(message = "token is required") @Size(max = 200, message = "token is too long") String token) {
    }

    /** El token no se devuelve completo: basta con saber que quedó registrado. */
    public record DeviceResponse(long id, String platform, boolean active, Instant lastSeenAt) {

        public static DeviceResponse from(DeviceToken device) {
            return new DeviceResponse(device.deviceTokenId(), device.platform().name(), device.active(),
                    device.lastSeenAt());
        }
    }
}
