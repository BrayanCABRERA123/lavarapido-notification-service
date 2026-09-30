package com.lavarapido.notification.domain.port.in;

import com.lavarapido.notification.domain.model.Notification;
import com.lavarapido.notification.domain.model.NotificationFilter;
import com.lavarapido.notification.domain.model.PageResult;

/**
 * La bandeja del usuario que llama. Todos los métodos reciben su user_id (sale del token):
 * nadie puede leer ni marcar notificaciones de otro.
 */
public interface NotificationInboxUseCase {

    PageResult<Notification> list(long userId, NotificationFilter filter, int page, int size);

    long countUnread(long userId);

    Notification markRead(long userId, long notificationId);

    Notification markUnread(long userId, long notificationId);

    /** Devuelve cuántas notificaciones quedaron marcadas. */
    int markAllRead(long userId);

    void delete(long userId, long notificationId);
}
