package com.lavarapido.notification.domain.port.in;

import com.lavarapido.notification.domain.model.DeviceToken;

/** Celulares que reciben push: la app registra su token al iniciar sesión y lo quita al salir. */
public interface DeviceRegistrationUseCase {

    DeviceToken register(long userId, String token, String platform);

    void unregister(long userId, String token);
}
