package com.lavarapido.notification.domain.port.in;

import com.lavarapido.notification.domain.model.NotificationTypeCode;

/** Lo necesario para crear (y enviar por push) una notificación a un usuario. */
public record SendNotificationCommand(long userId, NotificationTypeCode type, String title, String message,
                                      String referenceEntity, Long referenceId) {
}
