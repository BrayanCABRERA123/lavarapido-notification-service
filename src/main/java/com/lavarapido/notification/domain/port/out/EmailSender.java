package com.lavarapido.notification.domain.port.out;

import com.lavarapido.notification.domain.model.Notification;

/**
 * Envía una notificación por correo. Como la push, es un extra: si falla, la notificación ya
 * quedó guardada en la bandeja.
 */
public interface EmailSender {

    void send(Notification notification, String toAddress, String recipientName);
}
