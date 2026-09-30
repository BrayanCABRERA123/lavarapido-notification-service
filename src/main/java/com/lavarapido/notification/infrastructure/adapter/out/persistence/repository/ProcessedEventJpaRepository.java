package com.lavarapido.notification.infrastructure.adapter.out.persistence.repository;

import com.lavarapido.notification.infrastructure.adapter.out.persistence.entity.ProcessedEventJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventJpaRepository extends JpaRepository<ProcessedEventJpaEntity, Long> {

    boolean existsByEventId(String eventId);
}
