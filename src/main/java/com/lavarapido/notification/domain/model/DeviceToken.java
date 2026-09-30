package com.lavarapido.notification.domain.model;

import com.lavarapido.notification.domain.exception.InvalidValueException;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Celular al que se le pueden mandar notificaciones push (tabla notification.device_token,
 * ADR-011). El token lo entrega Expo al instalar la app y pedir permiso de notificaciones.
 *
 * Un mismo celular puede pasar de un usuario a otro (cerrar sesión y entrar con otra cuenta):
 * por eso registrar el token lo reasigna al usuario nuevo en vez de duplicarlo.
 */
public final class DeviceToken {

    // formato de Expo: ExponentPushToken[xxxxxxxx] (o ExpoPushToken[...] en versiones nuevas)
    private static final Pattern EXPO_TOKEN = Pattern.compile("^Expo(nent)?PushToken\\[[A-Za-z0-9_-]{10,}]$");

    private final Long deviceTokenId;
    private long userId;
    private final String token;
    private DevicePlatform platform;
    private boolean active;
    private Instant lastSeenAt;

    private DeviceToken(Long deviceTokenId, long userId, String token, DevicePlatform platform, boolean active,
                        Instant lastSeenAt) {
        this.deviceTokenId = deviceTokenId;
        this.userId = userId;
        this.token = token;
        this.platform = platform;
        this.active = active;
        this.lastSeenAt = lastSeenAt;
    }

    public static DeviceToken register(long userId, String token, DevicePlatform platform, Instant now) {
        if (userId <= 0) {
            throw new InvalidValueException("INVALID_RECIPIENT", "The user id must be positive");
        }
        return new DeviceToken(null, userId, validToken(token), Objects.requireNonNull(platform, "platform"), true,
                Objects.requireNonNull(now, "now"));
    }

    public static DeviceToken restore(long deviceTokenId, long userId, String token, DevicePlatform platform,
                                      boolean active, Instant lastSeenAt) {
        return new DeviceToken(deviceTokenId, userId, token, platform, active, lastSeenAt);
    }

    /** El mismo celular vuelve a registrarse (quizá con otra cuenta): queda activo y del usuario nuevo. */
    public void reassignTo(long newUserId, DevicePlatform newPlatform, Instant now) {
        this.userId = newUserId;
        this.platform = Objects.requireNonNull(newPlatform, "platform");
        this.active = true;
        this.lastSeenAt = Objects.requireNonNull(now, "now");
    }

    /** Cerró sesión o Expo dijo que la app ya no está instalada (DeviceNotRegistered). */
    public void deactivate() {
        this.active = false;
    }

    public static String validToken(String token) {
        String clean = token == null ? "" : token.strip();
        if (!EXPO_TOKEN.matcher(clean).matches()) {
            throw new InvalidValueException("INVALID_DEVICE_TOKEN", "The push token does not have the Expo format");
        }
        return clean;
    }

    public Long deviceTokenId() {
        return deviceTokenId;
    }

    public long userId() {
        return userId;
    }

    public String token() {
        return token;
    }

    public DevicePlatform platform() {
        return platform;
    }

    public boolean active() {
        return active;
    }

    public Instant lastSeenAt() {
        return lastSeenAt;
    }
}
