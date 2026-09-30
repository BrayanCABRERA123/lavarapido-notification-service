package com.lavarapido.notification.domain.model;

import com.lavarapido.notification.domain.exception.InvalidValueException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("DeviceToken")
class DeviceTokenTest {

    private static final Instant NOW = Instant.parse("2026-09-30T15:00:00Z");
    private static final String TOKEN = "ExponentPushToken[xxxxxxxxxxxxxxxxxxxxxx]";

    @ParameterizedTest
    @ValueSource(strings = {"ExponentPushToken[abcDEF123456789]", "ExpoPushToken[abc_DEF-123456789]"})
    @DisplayName("acepta los formatos de token de Expo")
    void acceptsExpoTokens(String token) {
        assertEquals(token, DeviceToken.validToken("  " + token + " "));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "ExponentPushToken[]", "ExponentPushToken[abc]", "fcm-token-123456789",
            "ExponentPushToken[abc def 123456]"})
    @DisplayName("rechaza lo que no es un token de Expo")
    void rejectsOtherTokens(String token) {
        assertThrows(InvalidValueException.class, () -> DeviceToken.validToken(token));
    }

    @Test
    @DisplayName("un celular registrado queda activo")
    void registersActive() {
        DeviceToken device = DeviceToken.register(7, TOKEN, DevicePlatform.ANDROID, NOW);

        assertTrue(device.active());
        assertEquals(7, device.userId());
    }

    @Test
    @DisplayName("el mismo celular con otra cuenta pasa al usuario nuevo y se reactiva")
    void reassignsToNewUser() {
        DeviceToken device = DeviceToken.register(7, TOKEN, DevicePlatform.ANDROID, NOW);
        device.deactivate();

        device.reassignTo(9, DevicePlatform.ANDROID, NOW.plusSeconds(60));

        assertEquals(9, device.userId());
        assertTrue(device.active());
    }

    @Test
    @DisplayName("desactivar deja de enviarle push")
    void deactivates() {
        DeviceToken device = DeviceToken.register(7, TOKEN, DevicePlatform.IOS, NOW);

        device.deactivate();

        assertFalse(device.active());
    }

    @Test
    @DisplayName("la plataforma solo puede ser ANDROID o IOS")
    void validatesPlatform() {
        assertEquals(DevicePlatform.ANDROID, DevicePlatform.of("android"));
        assertThrows(InvalidValueException.class, () -> DevicePlatform.of("windows"));
    }
}
