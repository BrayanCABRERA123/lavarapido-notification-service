package com.lavarapido.notification.infrastructure.adapter.in.web.dto;

import com.lavarapido.notification.domain.model.Notification;

import java.time.Instant;

/**
 * Notificación como la recibe el frontend. type es el code estable (para traducir); category es
 * la pestaña donde se muestra; reference* permite abrir la pantalla de la reserva o el pago.
 */
public record NotificationResponse(long id, String type, String category, String title, String message,
                                   String referenceEntity, Long referenceId, boolean read, Instant readAt,
                                   Instant sentAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(notification.notificationId(), notification.type().name(),
                notification.category().name(), notification.title(), notification.message(),
                notification.referenceEntity(), notification.referenceId(), notification.isRead(),
                notification.readAt(), notification.sentAt());
    }
}
