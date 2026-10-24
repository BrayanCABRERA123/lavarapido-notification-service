package com.lavarapido.notification.application.delivery;

import com.lavarapido.notification.domain.model.Notification;
import com.lavarapido.notification.domain.model.PendingDelivery;
import com.lavarapido.notification.domain.port.out.EmailSender;

import java.util.Objects;

/** Manda la notificación al correo de quien la recibe, con saludo y pie en su idioma. */
public record EmailDeliveryCommand(Notification notification, String address, String recipientName,
                                   String language, EmailSender sender) implements DeliveryCommand {

    public EmailDeliveryCommand {
        Objects.requireNonNull(notification, "notification");
        Objects.requireNonNull(sender, "sender");
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("An email delivery needs an address");
        }
        address = address.strip();
    }

    @Override
    public String channel() {
        return "email";
    }

    @Override
    public void execute() {
        sender.send(notification, address, recipientName, language);
    }

    @Override
    public PendingDelivery toPending() {
        return PendingDelivery.email(notification.notificationId(), address, recipientName, language);
    }

    // el correo de la persona no va al log (solo el canal y la notificación)
    @Override
    public String toString() {
        return "EmailDeliveryCommand[notification=" + notification.notificationId() + "]";
    }
}
