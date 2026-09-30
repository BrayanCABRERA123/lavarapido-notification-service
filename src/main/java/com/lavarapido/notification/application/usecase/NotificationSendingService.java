package com.lavarapido.notification.application.usecase;

import com.lavarapido.notification.domain.model.DeviceToken;
import com.lavarapido.notification.domain.model.Notification;
import com.lavarapido.notification.domain.port.in.SendNotificationCommand;
import com.lavarapido.notification.domain.port.in.SendNotificationUseCase;
import com.lavarapido.notification.domain.port.out.DeviceTokenRepository;
import com.lavarapido.notification.domain.port.out.NotificationRepository;
import com.lavarapido.notification.domain.port.out.PushSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * Guarda la notificación en la bandeja y la manda por push a los celulares del usuario.
 * El push es un extra: si falla o el usuario no tiene celular registrado, la notificación igual
 * queda guardada y la ve en la campanita.
 */
@Service
public class NotificationSendingService implements SendNotificationUseCase {

    private static final Logger log = LoggerFactory.getLogger(NotificationSendingService.class);

    private final NotificationRepository notifications;
    private final DeviceTokenRepository devices;
    private final PushSender pushSender;
    private final Clock clock;

    public NotificationSendingService(NotificationRepository notifications, DeviceTokenRepository devices,
                                      PushSender pushSender, Clock clock) {
        this.notifications = notifications;
        this.devices = devices;
        this.pushSender = pushSender;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Notification send(SendNotificationCommand command) {
        Notification saved = notifications.save(Notification.create(command.userId(), command.type(),
                command.title(), command.message(), command.referenceEntity(), command.referenceId(),
                clock.instant()));

        List<DeviceToken> targets = devices.findActiveByUser(saved.userId());
        if (!targets.isEmpty()) {
            pushSender.send(saved, targets);
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
}
