package com.lavarapido.notification.application.usecase;

import com.lavarapido.notification.application.delivery.DeliveryCommand;
import com.lavarapido.notification.application.delivery.DeliveryInvoker;
import com.lavarapido.notification.application.delivery.DeliveryInvoker.DeliveryReport;
import com.lavarapido.notification.domain.model.Notification;
import com.lavarapido.notification.domain.port.in.SendNotificationCommand;
import com.lavarapido.notification.domain.port.in.SendNotificationUseCase;
import com.lavarapido.notification.domain.port.out.DeviceTokenRepository;
import com.lavarapido.notification.domain.port.out.EmailSender;
import com.lavarapido.notification.domain.port.out.NotificationRepository;
import com.lavarapido.notification.domain.port.out.PushSender;
import com.lavarapido.notification.domain.port.out.UserContactDirectory;
import com.lavarapido.notification.domain.port.out.UserContactDirectory.UserContact;
import com.lavarapido.notification.domain.service.NotificationTexts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * Guarda la notificación en la bandeja y la entrega por los canales externos (push a los
 * celulares del usuario y, si el comando trae correo, email). Push y correo son extras: si fallan
 * o no aplican, la notificación igual queda guardada y la ve en la campanita.
 *
 * Las entregas siguen el patrón Command: DeliveryPlanner arma un comando por canal (respetando
 * los interruptores del usuario) y DeliveryInvoker los ejecuta aislando los fallos.
 */
@Service
public class NotificationSendingService implements SendNotificationUseCase {

    private static final Logger log = LoggerFactory.getLogger(NotificationSendingService.class);

    private final NotificationRepository notifications;
    private final UserContactDirectory contacts;
    private final DeliveryPlanner planner;
    private final DeliveryInvoker invoker = new DeliveryInvoker();
    private final Clock clock;
    private final NotificationTexts texts = NotificationTexts.defaults();

    public NotificationSendingService(NotificationRepository notifications, DeviceTokenRepository devices,
                                      PushSender pushSender, EmailSender emailSender,
                                      UserContactDirectory contacts, Clock clock) {
        this.notifications = notifications;
        this.contacts = contacts;
        this.planner = new DeliveryPlanner(devices, pushSender, emailSender);
        this.clock = clock;
    }

    @Override
    @Transactional
    public Notification send(SendNotificationCommand command) {
        // el aviso se escribe en el idioma de la cuenta de quien lo recibe (RF-020); un mensaje escrito
        // a mano por el administrador no trae plantilla y sale tal cual
        boolean contactRead = command.isLocalized();
        Optional<UserContact> contact = contactRead ? contacts.contactOf(command.userId()) : Optional.empty();
        String language = NotificationTexts.language(contact.map(UserContact::language).orElse(null));
        SendNotificationCommand localized = command.in(language, texts);
        Notification saved = notifications.save(Notification.create(localized.userId(), localized.type(),
                localized.title(), localized.message(), localized.referenceEntity(), localized.referenceId(),
                clock.instant()));

        List<DeliveryCommand> deliveries = planner.plan(saved, localized, language,
                () -> contactRead ? contact : contacts.contactOf(saved.userId()));
        DeliveryReport report = invoker.run(saved.notificationId(), deliveries);
        log.info("Notification {} ({}) created for user {}, delivered by {}{}", saved.notificationId(),
                saved.type(), saved.userId(), report.delivered().isEmpty() ? "inbox only" : report.delivered(),
                report.failed().isEmpty() ? "" : ", failed by " + report.failed());
        return saved;
    }

    @Override
    @Transactional
    public List<Notification> sendAll(List<SendNotificationCommand> commands) {
        return commands.stream().map(this::send).toList();
    }
}
