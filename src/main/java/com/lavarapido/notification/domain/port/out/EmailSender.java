package com.lavarapido.notification.domain.port.out;

import com.lavarapido.notification.domain.model.Notification;

/**
 * Envía una notificación por correo. Como la push, es un extra: si falla, la notificación ya
 * quedó guardada en la bandeja.
 */
public interface EmailSender {

    void send(Notification notification, String toAddress, String recipientName);

    /** Con el saludo y el pie del correo en el idioma de la cuenta (es, en, fr, pt). */
    default void send(Notification notification, String toAddress, String recipientName, String language) {
        send(notification, toAddress, recipientName);
    }
}
