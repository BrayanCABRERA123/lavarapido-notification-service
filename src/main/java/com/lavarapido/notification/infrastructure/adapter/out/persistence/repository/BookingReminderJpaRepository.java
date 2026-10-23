package com.lavarapido.notification.infrastructure.adapter.out.persistence.repository;

import com.lavarapido.notification.infrastructure.adapter.out.persistence.entity.BookingReminderJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface BookingReminderJpaRepository extends JpaRepository<BookingReminderJpaEntity, Long> {

    /** Incluye los cancelados y enviados: uq_booking_reminder_lead no es filtrado. */
    List<BookingReminderJpaEntity> findByBookingId(long bookingId);

    @Query("""
            SELECT r FROM BookingReminderJpaEntity r
            WHERE r.sentAt IS NULL AND r.deletedAt IS NULL AND r.remindAt <= :now
            ORDER BY r.remindAt ASC
            """)
    List<BookingReminderJpaEntity> findDue(@Param("now") Instant now, Pageable page);
}
