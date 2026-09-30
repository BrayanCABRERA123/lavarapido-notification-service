package com.lavarapido.notification.application.usecase;

import com.lavarapido.notification.domain.model.DevicePlatform;
import com.lavarapido.notification.domain.model.DeviceToken;
import com.lavarapido.notification.domain.port.in.DeviceRegistrationUseCase;
import com.lavarapido.notification.domain.port.out.DeviceTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/** Registra el celular que recibe push. Idempotente: registrar el mismo token dos veces no duplica. */
@Service
public class DeviceRegistrationService implements DeviceRegistrationUseCase {

    private final DeviceTokenRepository devices;
    private final Clock clock;

    public DeviceRegistrationService(DeviceTokenRepository devices, Clock clock) {
        this.devices = devices;
        this.clock = clock;
    }

    @Override
    @Transactional
    public DeviceToken register(long userId, String token, String platform) {
        String validToken = DeviceToken.validToken(token);
        DevicePlatform devicePlatform = DevicePlatform.of(platform);

        DeviceToken device = devices.findByToken(validToken)
                .map(existing -> {
                    // el mismo celular con otra cuenta: las push pasan a la cuenta nueva
                    existing.reassignTo(userId, devicePlatform, clock.instant());
                    return existing;
                })
                .orElseGet(() -> DeviceToken.register(userId, validToken, devicePlatform, clock.instant()));
        return devices.save(device);
    }

    /**
     * Al cerrar sesión la app quita su token. Solo lo desactiva si es de quien llama: nadie puede
     * apagar las push del celular de otro. Si no existe, no pasa nada (la app puede reintentar).
     */
    @Override
    @Transactional
    public void unregister(long userId, String token) {
        devices.findByToken(DeviceToken.validToken(token))
                .filter(device -> device.userId() == userId)
                .ifPresent(device -> {
                    device.deactivate();
                    devices.save(device);
                });
    }
}
