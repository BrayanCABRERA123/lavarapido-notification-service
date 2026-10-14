package com.lavarapido.notification.infrastructure.adapter.out.persistence.repository;

import java.time.Instant;
import java.util.List;
import com.lavarapido.notification.infrastructure.adapter.out.persistence.entity.BookingTrackingJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingTrackingJpaRepository extends JpaRepository<BookingTrackingJpaEntity, Long> {

    List<BookingTrackingJpaEntity> findByOperatorUserIdIsNullAndCancelledFalseAndUnassignedAlertAtIsNullAndScheduledStartBetween(
            Instant from, Instant to);
}
