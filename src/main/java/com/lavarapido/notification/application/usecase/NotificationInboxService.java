package com.lavarapido.notification.application.usecase;

import com.lavarapido.notification.domain.exception.InvalidValueException;
import com.lavarapido.notification.domain.exception.NotificationNotFoundException;
import com.lavarapido.notification.domain.model.Notification;
import com.lavarapido.notification.domain.model.NotificationFilter;
import com.lavarapido.notification.domain.model.PageResult;
import com.lavarapido.notification.domain.port.in.NotificationInboxUseCase;
import com.lavarapido.notification.domain.port.out.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/** La bandeja del usuario: listar, contar sin leer, marcar y borrar (lógico). */
@Service
public class NotificationInboxService implements NotificationInboxUseCase {

    static final int MAX_PAGE_SIZE = 100;

    private final NotificationRepository notifications;
    private final Clock clock;

    public NotificationInboxService(NotificationRepository notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Notification> list(long userId, NotificationFilter filter, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidValueException("INVALID_PAGE", "page must be >= 0 and size between 1 and " + MAX_PAGE_SIZE);
        }
        NotificationFilter safe = filter == null ? NotificationFilter.none() : filter;
        if (safe.from() != null && safe.to() != null && safe.from().isAfter(safe.to())) {
            throw new InvalidValueException("INVALID_DATE_RANGE", "from must be on or before to");
        }
        return notifications.findActiveByUser(userId, safe, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnread(long userId) {
        return notifications.countUnread(userId);
    }

    @Override
    @Transactional
    public Notification markRead(long userId, long notificationId) {
        Notification notification = ownedBy(userId, notificationId);
        notification.markRead(clock.instant());
        return notifications.save(notification);
    }

    @Override
    @Transactional
    public Notification markUnread(long userId, long notificationId) {
        Notification notification = ownedBy(userId, notificationId);
        notification.markUnread();
        return notifications.save(notification);
    }

    @Override
    @Transactional
    public int markAllRead(long userId) {
        return notifications.markAllRead(userId, clock.instant());
    }

    @Override
    @Transactional
    public void delete(long userId, long notificationId) {
        Notification notification = ownedBy(userId, notificationId);
        notification.delete(clock.instant());
        notifications.save(notification);
    }

    // la de otro usuario responde igual que una que no existe (404)
    private Notification ownedBy(long userId, long notificationId) {
        return notifications.findActive(notificationId)
                .filter(notification -> notification.belongsTo(userId))
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));
    }
}
