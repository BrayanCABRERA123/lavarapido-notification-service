package com.lavarapido.notification.domain.exception;

/**
 * La notificación no existe, está borrada o es de otro usuario. Los tres casos responden igual
 * a propósito: decir "es de otro" ya sería contar que existe.
 */
public class NotificationNotFoundException extends DomainException {

    public NotificationNotFoundException(long notificationId) {
        super("NOTIFICATION_NOT_FOUND", "Notification " + notificationId + " not found for this user");
    }
}
