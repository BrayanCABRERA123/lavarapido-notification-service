package com.lavarapido.notification.application.usecase;

import com.lavarapido.notification.domain.model.DeviceToken;
import com.lavarapido.notification.domain.model.Notification;
import com.lavarapido.notification.domain.port.in.SendNotificationCommand;
import com.lavarapido.notification.domain.port.in.SendNotificationUseCase;
import com.lavarapido.notification.domain.port.out.DeviceTokenRepository;
import com.lavarapido.notification.domain.port.out.EmailSender;
import com.lavarapido.notification.domain.port.out.NotificationRepository;
import com.lavarapido.notification.domain.port.out.PushSender;
import com.lavarapido.notification.domain.port.out.UserContactDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * Guarda la notificación en la bandeja, la manda por push a los celulares del usuario y, si el
 * comando trae correo, también por email. Push y correo son extras: si fallan o no aplican, la
 * notificación igual queda guardada y la ve en la campanita.
 *
 * El push respeta los interruptores del usuario (NotificationChannels): solo se consultan a
 * security-service si tiene celulares registrados, y si no responde se envía igual.
 */
@Service
public class NotificationSendingService implements SendNotificationUseCase {

    private static final Logger log = LoggerFactory.getLogger(NotificationSendingService.class);

    private final NotificationRepository notifications;
    private final DeviceTokenRepository devices;
    private final PushSender pushSender;
    private final EmailSender emailSender;
    private final UserContactDirectory contacts;
    private final Clock clock;

    public NotificationSendingService(NotificationRepository notifications, DeviceTokenRepository devices,
                                      PushSender pushSender, EmailSender emailSender,
                                      UserContactDirectory contacts, Clock clock) {
        this.notifications = notifications;
        this.devices = devices;
        this.pushSender = pushSender;
        this.emailSender = emailSender;
        this.contacts = contacts;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Notification send(SendNotificationCommand command) {
        Notification saved = notifications.save(Notification.create(command.userId(), command.type(),
                command.title(), command.message(), command.referenceEntity(), command.referenceId(),
                clock.instant()));

        List<DeviceToken> targets = devices.findActiveByUser(saved.userId());
        if (!targets.isEmpty() && wantsPush(saved)) {
            pushSender.send(saved, targets);
        } else {
            targets = List.of();
        }
        if (command.hasEmail()) {
            emailSender.send(saved, command.email().strip(), command.recipientName());
        }
        log.info("Notification {} ({}) created for user {}, push to {} device(s)",
                saved.notificationId(), saved.type(), saved.userId(), targets.size());
        return saved;
    }

    @Override
    @Transactional
    public List<Notification> sendAll(List<SendNotificationCommand> commands) {
        return commands.stream().map(this::send).toList();
    }

    // sin respuesta de security-service se envía: es mejor avisar de más que dejar de avisar
    private boolean wantsPush(Notification notification) {
        return contacts.contactOf(notification.userId())
                .map(contact -> NotificationChannels.wantsPush(contact, notification.type()))
                .orElse(true);
    }
}
