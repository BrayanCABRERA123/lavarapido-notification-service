package com.lavarapido.notification.infrastructure.adapter.out.persistence.repository;

import com.lavarapido.notification.infrastructure.adapter.out.persistence.entity.BookingTrackingJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingTrackingJpaRepository extends JpaRepository<BookingTrackingJpaEntity, Long> {
}
