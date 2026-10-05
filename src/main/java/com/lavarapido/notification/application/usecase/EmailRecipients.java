package com.lavarapido.notification.application.usecase;

import com.lavarapido.notification.domain.model.NotificationTypeCode;
import com.lavarapido.notification.domain.port.in.SendNotificationCommand;
import com.lavarapido.notification.domain.port.out.UserContactDirectory;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * Decide qué notificaciones también van por correo y le pone el correo actual del usuario,
 * pedido a security-service. Hoy: reservas (creada, confirmada, cancelada) y recordatorios.
 * La bienvenida ya trae su correo en el evento.
 *
 * Si no se puede obtener el correo, o la cuenta está desactivada, la notificación sigue igual
 * (bandeja + push) sin correo.
 */
@Component
public class EmailRecipients {

    private static final Set<NotificationTypeCode> EMAILED = EnumSet.of(
            NotificationTypeCode.BOOKING_CREATED,
            NotificationTypeCode.BOOKING_CONFIRMED,
            NotificationTypeCode.BOOKING_CANCELLED,
            NotificationTypeCode.BOOKING_REMINDER);

    private final UserContactDirectory contacts;

    public EmailRecipients(UserContactDirectory contacts) {
        this.contacts = contacts;
    }

    public SendNotificationCommand withEmail(SendNotificationCommand command) {
        if (command.hasEmail() || !EMAILED.contains(command.type())) {
            return command;
        }
        return contacts.contactOf(command.userId())
                .filter(UserContactDirectory.UserContact::active)
                .filter(contact -> contact.email() != null && !contact.email().isBlank())
                .map(contact -> new SendNotificationCommand(command.userId(), command.type(), command.title(),
                        command.message(), command.referenceEntity(), command.referenceId(), contact.email(),
                        contact.firstName()))
                .orElse(command);
    }
}
