package com.lavarapido.notification.domain.port.out;

import com.lavarapido.notification.domain.model.Notification;
import com.lavarapido.notification.domain.model.NotificationFilter;
import com.lavarapido.notification.domain.model.PageResult;

import java.time.Instant;
import java.util.Optional;

/** Persistencia de notificaciones. Las borradas (deleted_at) nunca se devuelven. */
public interface NotificationRepository {

    Notification save(Notification notification);

    Optional<Notification> findActive(long notificationId);

    PageResult<Notification> findActiveByUser(long userId, NotificationFilter filter, int page, int size);

    long countUnread(long userId);

    int markAllRead(long userId, Instant readAt);
}
