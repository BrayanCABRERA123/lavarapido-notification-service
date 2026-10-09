package com.lavarapido.notification.application.usecase;

import com.lavarapido.notification.domain.model.NotificationTypeCode;
import com.lavarapido.notification.domain.port.in.SendNotificationCommand;
import com.lavarapido.notification.domain.port.out.UserContactDirectory;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * Decide qué notificaciones también van por correo y le pone el correo actual del usuario,
 * pedido a security-service. Hoy: reservas (creada, confirmada, cancelada), recordatorios,
 * canje de cupones, cupón desbloqueado con puntos, el resultado de un pago (aprobado, rechazado,
 * reembolsado) y el aviso de inspección del vehículo: todo lo que le cambia algo al cliente. La
 * bienvenida ya trae su correo en el evento.
 *
 * El avance del servicio (operario asignado, iniciado, terminado), los puntos ganados y los avisos
 * del personal (operario y administrador) se quedan en la bandeja y el push, sin correo.
 *
 * Si no se puede obtener el correo, o la cuenta está desactivada, la notificación sigue igual
 * (bandeja + push) sin correo. Tampoco se envía si el usuario apagó ese canal en Configuración >
 * Notificaciones: correo de recordatorios o promociones (NotificationChannels).
 */
@Component
public class EmailRecipients {

    private static final Set<NotificationTypeCode> EMAILED = EnumSet.of(
            NotificationTypeCode.BOOKING_CREATED,
            NotificationTypeCode.BOOKING_CONFIRMED,
            NotificationTypeCode.BOOKING_CANCELLED,
            NotificationTypeCode.BOOKING_REMINDER,
            NotificationTypeCode.PROMOTION_REDEEMED,
            NotificationTypeCode.PAYMENT_CONFIRMED,
            NotificationTypeCode.PAYMENT_REJECTED,
            NotificationTypeCode.PAYMENT_REFUNDED,
            NotificationTypeCode.PROMOTION_AVAILABLE,
            NotificationTypeCode.INSPECTION_REPORT);

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
                // el usuario pudo apagar el correo de recordatorios o las promociones
                .filter(contact -> NotificationChannels.wantsEmail(contact, command.type()))
                .map(contact -> new SendNotificationCommand(command.userId(), command.type(), command.title(),
                        command.message(), command.referenceEntity(), command.referenceId(), contact.email(),
                        contact.firstName()))
                .orElse(command);
    }
}
