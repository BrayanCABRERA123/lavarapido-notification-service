package com.lavarapido.notification.domain.exception;

/**
 * El administrador pidió enviar a mano un tipo que solo generan los eventos (ej. PAYMENT_CONFIRMED):
 * un "pago aprobado" escrito a mano llegaría a la bandeja y al correo del cliente como si fuera real.
 */
public class NotificationTypeNotAllowedException extends DomainException {

    public NotificationTypeNotAllowedException(String typeCode) {
        super("NOTIFICATION_TYPE_NOT_ALLOWED", "Notification type " + typeCode + " cannot be sent by an administrator");
    }
}
