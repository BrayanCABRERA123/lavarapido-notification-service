package com.lavarapido.notification.domain.port.out;

import com.lavarapido.notification.domain.model.DeviceToken;

import java.util.List;
import java.util.Optional;

public interface DeviceTokenRepository {

    DeviceToken save(DeviceToken device);

    Optional<DeviceToken> findByToken(String token);

    List<DeviceToken> findActiveByUser(long userId);

    /** Expo avisó que la app ya no está instalada en ese celular. */
    void deactivate(String token);
}
