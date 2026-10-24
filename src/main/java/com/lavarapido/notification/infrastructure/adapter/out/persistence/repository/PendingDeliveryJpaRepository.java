package com.lavarapido.notification.infrastructure.adapter.out.persistence.repository;

import com.lavarapido.notification.infrastructure.adapter.out.persistence.entity.PendingDeliveryJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface PendingDeliveryJpaRepository extends JpaRepository<PendingDeliveryJpaEntity, Long> {

    @Query("""
            SELECT d.id FROM PendingDeliveryJpaEntity d
            WHERE d.deliveredAt IS NULL AND d.failedAt IS NULL AND d.nextAttemptAt <= :now
            ORDER BY d.nextAttemptAt ASC
            """)
    List<Long> findDueIds(@Param("now") Instant now, Pageable page);

    /** Tomarla en una sola sentencia: 1 si se tomó, 0 si otro ya la tenía o ya salió. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE PendingDeliveryJpaEntity d SET d.attempts = d.attempts + 1, d.nextAttemptAt = :lease
            WHERE d.id = :id AND d.deliveredAt IS NULL AND d.failedAt IS NULL AND d.nextAttemptAt <= :now
            """)
    int claim(@Param("id") long id, @Param("now") Instant now, @Param("lease") Instant lease);

    /** Entregada: se borran la dirección y el nombre (ya no hacen falta). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE PendingDeliveryJpaEntity d SET d.deliveredAt = :at, d.lastError = NULL,
                d.emailAddress = NULL, d.recipientName = NULL
            WHERE d.id = :id
            """)
    int markDelivered(@Param("id") long id, @Param("at") Instant at);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PendingDeliveryJpaEntity d SET d.nextAttemptAt = :next, d.lastError = :error WHERE d.id = :id")
    int markRetry(@Param("id") long id, @Param("next") Instant next, @Param("error") String error);

    /** Abandonada: también sin dirección ni nombre. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE PendingDeliveryJpaEntity d SET d.failedAt = :at, d.lastError = :error,
                d.emailAddress = NULL, d.recipientName = NULL
            WHERE d.id = :id
            """)
    int markFailed(@Param("id") long id, @Param("at") Instant at, @Param("error") String error);
}
