package com.lavarapido.notification.infrastructure.adapter.out.persistence.repository;

import com.lavarapido.notification.infrastructure.adapter.out.persistence.entity.NotificationTypeJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationTypeJpaRepository extends JpaRepository<NotificationTypeJpaEntity, Short> {
}
