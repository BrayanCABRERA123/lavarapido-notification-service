package com.lavarapido.notification.application.usecase;

import com.lavarapido.notification.application.delivery.DeliveryCommand;
import com.lavarapido.notification.application.delivery.DeliveryInvoker;
import com.lavarapido.notification.application.delivery.DeliveryInvoker.DeliveryReport;
import com.lavarapido.notification.application.delivery.EmailDeliveryCommand;
import com.lavarapido.notification.application.delivery.PushDeliveryCommand;
import com.lavarapido.notification.application.fake.Fakes.InMemoryContacts;
import com.lavarapido.notification.application.fake.Fakes.InMemoryDevices;
import com.lavarapido.notification.application.fake.Fakes.InMemoryNotifications;
import com.lavarapido.notification.application.fake.Fakes.RecordingEmailSender;
import com.lavarapido.notification.application.fake.Fakes.RecordingPushSender;
import com.lavarapido.notification.domain.model.DevicePlatform;
import com.lavarapido.notification.domain.model.DeviceToken;
import com.lavarapido.notification.domain.model.Notification;
import com.lavarapido.notification.domain.model.NotificationTypeCode;
import com.lavarapido.notification.domain.port.in.SendNotificationCommand;
import com.lavarapido.notification.domain.port.out.EmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Entregas por canal (patrón Command)")
class DeliveryCommandsTest {

    private static final long ANA = 7;
    private static final String TOKEN = "ExponentPushToken[aaaaaaaaaaaaaaaaaaaa]";

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-15T15:00:00Z"), ZoneOffset.UTC);

    private InMemoryNotifications notifications;
    private InMemoryDevices devices;
    private RecordingPushSender push;
    private RecordingEmailSender email;
    private InMemoryContacts contacts;

    @BeforeEach
    void setUp() {
        notifications = new InMemoryNotifications();
        devices = new InMemoryDevices();
        push = new RecordingPushSender();
        email = new RecordingEmailSender();
        contacts = new InMemoryContacts();
    }

    private Notification saved(NotificationTypeCode type) {
        return notifications.save(Notification.create(ANA, type, "Título", "Mensaje", null, null, clock.instant()));
    }

    private void registerPhone() {
        devices.save(DeviceToken.register(ANA, TOKEN, DevicePlatform.ANDROID, clock.instant()));
    }

    private SendNotificationCommand command(NotificationTypeCode type, String mail) {
        SendNotificationCommand command = new SendNotificationCommand(ANA, type, "Título", "Mensaje", null, null);
        return mail == null ? command : command.withEmail(mail, "Ana");
    }

    @Test
    @DisplayName("con celular y correo se planea una entrega por cada canal, en orden push y correo")
    void plansOneCommandPerChannel() {
        registerPhone();
        DeliveryPlanner planner = new DeliveryPlanner(devices, push, email);

        List<DeliveryCommand> plan = planner.plan(saved(NotificationTypeCode.USER_WELCOME),
                command(NotificationTypeCode.USER_WELCOME, "ana@correo.com"), "es", Optional::empty);

        assertEquals(2, plan.size());
        assertInstanceOf(PushDeliveryCommand.class, plan.get(0));
        assertInstanceOf(EmailDeliveryCommand.class, plan.get(1));
    }

    @Test
    @DisplayName("sin celulares no hay push y ni siquiera se consulta a security-service")
    void withoutPhonesThereIsNoPushAndNoContactLookup() {
        DeliveryPlanner planner = new DeliveryPlanner(devices, push, email);
        List<String> lookups = new ArrayList<>();

        List<DeliveryCommand> plan = planner.plan(saved(NotificationTypeCode.BOOKING_CONFIRMED),
                command(NotificationTypeCode.BOOKING_CONFIRMED, null), "es", () -> {
                    lookups.add("contact");
                    return Optional.empty();
                });

        assertTrue(plan.isEmpty());
        assertTrue(lookups.isEmpty());
    }

    @Test
    @DisplayName("si el usuario apagó las push, no se planea la push aunque tenga celular")
    void respectsThePushSwitch() {
        registerPhone();
        contacts.withChannels(ANA, false, true, true);
        DeliveryPlanner planner = new DeliveryPlanner(devices, push, email);

        List<DeliveryCommand> plan = planner.plan(saved(NotificationTypeCode.BOOKING_CONFIRMED),
                command(NotificationTypeCode.BOOKING_CONFIRMED, null), "es", () -> contacts.contactOf(ANA));

        assertTrue(plan.isEmpty());
    }

    @Test
    @DisplayName("si un canal falla, los demás igual se ejecutan y el reporte lo dice")
    void aFailingChannelDoesNotStopTheOthers() {
        registerPhone();
        Notification notification = saved(NotificationTypeCode.USER_WELCOME);
        EmailSender broken = (n, to, name) -> {
            throw new IllegalStateException("SMTP down");
        };
        List<DeliveryCommand> plan = List.of(
                new EmailDeliveryCommand(notification, "ana@correo.com", "Ana", "es", broken),
                new PushDeliveryCommand(notification, devices.findActiveByUser(ANA), push));

        DeliveryReport report = new DeliveryInvoker().run(notification.notificationId(), plan);

        assertEquals(List.of("push"), report.delivered());
        assertEquals(List.of("email"), report.failed());
        assertEquals(1, push.sent.size());
    }

    @Test
    @DisplayName("si el correo falla al enviar, la notificación igual queda en la bandeja y la push sale")
    void theInboxKeepsTheNotificationWhenTheEmailFails() {
        registerPhone();
        EmailSender broken = (n, to, name) -> {
            throw new IllegalStateException("SMTP down");
        };
        NotificationSendingService sender = new NotificationSendingService(notifications, devices, push, broken,
                contacts, clock);

        Notification sent = sender.send(command(NotificationTypeCode.USER_WELCOME, "ana@correo.com"));

        assertTrue(notifications.findActive(sent.notificationId()).isPresent());
        assertEquals(1, push.sent.size());
    }

    @Test
    @DisplayName("una entrega sin destino no se puede crear")
    void aDeliveryNeedsATarget() {
        Notification notification = saved(NotificationTypeCode.USER_WELCOME);

        assertThrows(IllegalArgumentException.class, () -> new PushDeliveryCommand(notification, List.of(), push));
        assertThrows(IllegalArgumentException.class,
                () -> new EmailDeliveryCommand(notification, " ", "Ana", "es", email));
    }
}
