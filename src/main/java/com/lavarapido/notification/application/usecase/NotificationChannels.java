package com.lavarapido.notification.application.usecase;

import com.lavarapido.notification.domain.model.NotificationTypeCode;
import com.lavarapido.notification.domain.port.out.UserContactDirectory.UserContact;

/**
 * Lo que el usuario apagó en Configuración > Notificaciones (se guarda en security-service):
 * - "Notificaciones push": ninguna notificación llega al celular por push.
 * - "Recordatorios por email": los recordatorios de reserva no llegan al correo.
 * - "Promociones y ofertas": los cupones desbloqueados no llegan ni al correo ni por push.
 *
 * La bandeja de la app siempre se llena: los interruptores solo deciden los canales externos.
 */
final class NotificationChannels {

    private NotificationChannels() {
    }

    /** Si el tipo puede ir al correo de esta persona según sus interruptores. */
    static boolean wantsEmail(UserContact contact, NotificationTypeCode type) {
        return switch (type) {
            case BOOKING_REMINDER -> contact.allowsEmailReminders();
            case PROMOTION_AVAILABLE -> contact.allowsPromotions();
            default -> true;
        };
    }

    /** Si el tipo puede llegar por push a sus celulares según sus interruptores. */
    static boolean wantsPush(UserContact contact, NotificationTypeCode type) {
        return contact.allowsPush() && (type != NotificationTypeCode.PROMOTION_AVAILABLE || contact.allowsPromotions());
    }
}
