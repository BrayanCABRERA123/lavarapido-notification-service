package com.lavarapido.notification.infrastructure.adapter.out.persistence.repository;

import com.lavarapido.notification.infrastructure.adapter.out.persistence.entity.NotificationJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/** Las borradas (deleted_at) nunca se devuelven ni se cuentan. */
public interface NotificationJpaRepository extends JpaRepository<NotificationJpaEntity, Long>,
        JpaSpecificationExecutor<NotificationJpaEntity> {

    Optional<NotificationJpaEntity> findByIdAndDeletedAtIsNull(long id);

    long countByUserIdAndReadFalseAndDeletedAtIsNull(long userId);

    /** Un solo UPDATE en vez de cargar todas: is_read y read_at cambian juntos (ck_notification_read). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE NotificationJpaEntity n
            SET n.read = true, n.readAt = :readAt
            WHERE n.userId = :userId AND n.read = false AND n.deletedAt IS NULL
            """)
    int markAllRead(@Param("userId") long userId, @Param("readAt") Instant readAt);
}
