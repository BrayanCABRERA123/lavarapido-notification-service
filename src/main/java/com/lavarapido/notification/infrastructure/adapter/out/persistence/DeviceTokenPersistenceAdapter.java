package com.lavarapido.notification.infrastructure.adapter.out.persistence;

import com.lavarapido.notification.domain.model.DevicePlatform;
import com.lavarapido.notification.domain.model.DeviceToken;
import com.lavarapido.notification.domain.port.out.DeviceTokenRepository;
import com.lavarapido.notification.infrastructure.adapter.out.persistence.entity.DeviceTokenJpaEntity;
import com.lavarapido.notification.infrastructure.adapter.out.persistence.repository.DeviceTokenJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
class DeviceTokenPersistenceAdapter implements DeviceTokenRepository {

    private final DeviceTokenJpaRepository devices;

    DeviceTokenPersistenceAdapter(DeviceTokenJpaRepository devices) {
        this.devices = devices;
    }

    @Override
    public DeviceToken save(DeviceToken device) {
        DeviceTokenJpaEntity entity = devices.findByToken(device.token())
                .orElseGet(() -> new DeviceTokenJpaEntity(device.token(), device.lastSeenAt()));
        entity.apply(device.userId(), device.platform().name(), device.active(), device.lastSeenAt());
        return toDomain(devices.save(entity));
    }

    @Override
    public Optional<DeviceToken> findByToken(String token) {
        return devices.findByToken(token).map(DeviceTokenPersistenceAdapter::toDomain);
    }

    @Override
    public List<DeviceToken> findActiveByUser(long userId) {
        return devices.findByUserIdAndActiveTrue(userId).stream().map(DeviceTokenPersistenceAdapter::toDomain).toList();
    }

    /** Lo llama el envío de push (fuera de la transacción de la petición), por eso abre la suya. */
    @Override
    @Transactional
    public void deactivate(String token) {
        devices.deactivateByToken(token);
    }

    private static DeviceToken toDomain(DeviceTokenJpaEntity entity) {
        return DeviceToken.restore(entity.getId(), entity.getUserId(), entity.getToken(),
                DevicePlatform.valueOf(entity.getPlatform()), entity.isActive(), entity.getLastSeenAt());
    }
}
