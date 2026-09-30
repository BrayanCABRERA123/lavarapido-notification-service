package com.lavarapido.notification.domain.port.in;

import com.lavarapido.notification.domain.model.NotificationTypeCode;

/**
 * Lo necesario para crear (y enviar por push) una notificación a un usuario.
 *
 * email y recipientName son opcionales: si vienen, la notificación también se envía por correo.
 * Hoy solo los trae la bienvenida (el evento UserRegistered incluye el correo, ADR-011); este
 * servicio no guarda correos de nadie.
 */
public record SendNotificationCommand(long userId, NotificationTypeCode type, String title, String message,
                                      String referenceEntity, Long referenceId, String email,
                                      String recipientName) {

    /** Notificación sin correo (bandeja + push). */
    public SendNotificationCommand(long userId, NotificationTypeCode type, String title, String message,
                                   String referenceEntity, Long referenceId) {
        this(userId, type, title, message, referenceEntity, referenceId, null, null);
    }

    public boolean hasEmail() {
        return email != null && !email.isBlank();
    }
}
