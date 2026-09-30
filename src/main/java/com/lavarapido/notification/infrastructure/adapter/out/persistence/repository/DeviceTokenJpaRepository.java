package com.lavarapido.notification.infrastructure.adapter.out.persistence.repository;

import com.lavarapido.notification.infrastructure.adapter.out.persistence.entity.DeviceTokenJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DeviceTokenJpaRepository extends JpaRepository<DeviceTokenJpaEntity, Long> {

    Optional<DeviceTokenJpaEntity> findByToken(String token);

    List<DeviceTokenJpaEntity> findByUserIdAndActiveTrue(long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE DeviceTokenJpaEntity d SET d.active = false WHERE d.token = :token")
    int deactivateByToken(@Param("token") String token);
}
