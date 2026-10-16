package com.lavarapido.notification.application.usecase;

import com.lavarapido.notification.application.delivery.DeliveryCommand;
import com.lavarapido.notification.application.delivery.EmailDeliveryCommand;
import com.lavarapido.notification.application.delivery.PushDeliveryCommand;
import com.lavarapido.notification.domain.model.DeviceToken;
import com.lavarapido.notification.domain.model.Notification;
import com.lavarapido.notification.domain.port.in.SendNotificationCommand;
import com.lavarapido.notification.domain.port.out.DeviceTokenRepository;
import com.lavarapido.notification.domain.port.out.EmailSender;
import com.lavarapido.notification.domain.port.out.PushSender;
import com.lavarapido.notification.domain.port.out.UserContactDirectory.UserContact;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Decide por qué canales externos sale una notificación ya guardada y arma un comando por cada
 * uno (patrón Command). No ejecuta nada: eso lo hace DeliveryInvoker.
 *
 * - push: si el usuario tiene celulares activos y sus interruptores lo permiten
 *   (NotificationChannels). Sin respuesta de security-service se envía: es mejor avisar de más
 *   que dejar de avisar.
 * - correo: solo si el comando trae correo (bienvenida, recordatorios, cupones; nunca avisos
 *   del personal).
 */
final class DeliveryPlanner {

    private final DeviceTokenRepository devices;
    private final PushSender pushSender;
    private final EmailSender emailSender;

    DeliveryPlanner(DeviceTokenRepository devices, PushSender pushSender, EmailSender emailSender) {
        this.devices = devices;
        this.pushSender = pushSender;
        this.emailSender = emailSender;
    }

    /**
     * @param contact se consulta solo si hace falta (hay celulares): evita llamar a
     *                security-service por cada aviso de quien no tiene la app
     */
    List<DeliveryCommand> plan(Notification saved, SendNotificationCommand command, String language,
                               Supplier<Optional<UserContact>> contact) {
        List<DeliveryCommand> commands = new ArrayList<>();
        List<DeviceToken> targets = devices.findActiveByUser(saved.userId());
        if (!targets.isEmpty() && wantsPush(saved, contact.get())) {
            commands.add(new PushDeliveryCommand(saved, targets, pushSender));
        }
        if (command.hasEmail()) {
            commands.add(new EmailDeliveryCommand(saved, command.email(), command.recipientName(), language,
                    emailSender));
        }
        return commands;
    }

    private static boolean wantsPush(Notification notification, Optional<UserContact> contact) {
        return contact.map(c -> NotificationChannels.wantsPush(c, notification.type())).orElse(true);
    }
}
