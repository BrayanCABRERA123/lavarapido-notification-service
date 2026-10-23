package com.lavarapido.notification.application.usecase;

import com.lavarapido.notification.application.fake.Fakes;
import com.lavarapido.notification.application.fake.Fakes.InMemoryDevices;
import com.lavarapido.notification.application.fake.Fakes.InMemoryNotifications;
import com.lavarapido.notification.application.fake.Fakes.InMemoryProcessedEvents;
import com.lavarapido.notification.application.fake.Fakes.RecordingEmailSender;
import com.lavarapido.notification.application.fake.Fakes.RecordingPushSender;
import com.lavarapido.notification.domain.exception.InvalidValueException;
import com.lavarapido.notification.domain.exception.NotificationNotFoundException;
import com.lavarapido.notification.domain.model.DevicePlatform;
import com.lavarapido.notification.domain.model.DomainEventEnvelope;
import com.lavarapido.notification.domain.model.Notification;
import com.lavarapido.notification.domain.model.NotificationCategory;
import com.lavarapido.notification.domain.model.NotificationFilter;
import com.lavarapido.notification.domain.model.NotificationTypeCode;
import com.lavarapido.notification.domain.port.in.SendNotificationCommand;
import com.lavarapido.notification.domain.service.EventNotificationFactory;
import com.lavarapido.notification.domain.service.StaffNotificationFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Casos de uso de notificaciones")
class NotificationUseCasesTest {

    private static final long ANA = 7;
    private static final long LUIS = 8;
    private static final String TOKEN = "ExponentPushToken[aaaaaaaaaaaaaaaaaaaa]";

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-30T15:00:00Z"), ZoneOffset.UTC);

    private InMemoryNotifications notifications;
    private InMemoryDevices devices;
    private RecordingPushSender push;
    private RecordingEmailSender email;
    private Fakes.InMemoryContacts contacts;
    private NotificationSendingService sender;
    private NotificationInboxService inbox;
    private DeviceRegistrationService registration;

    @BeforeEach
    void setUp() {
        notifications = new InMemoryNotifications();
        devices = new InMemoryDevices();
        push = new RecordingPushSender();
        email = new RecordingEmailSender();
        contacts = new Fakes.InMemoryContacts();
        sender = new NotificationSendingService(notifications, devices, push, email, contacts, clock);
        inbox = new NotificationInboxService(notifications, clock);
        registration = new DeviceRegistrationService(devices, clock);
    }

    private Notification send(long userId, NotificationTypeCode type) {
        return sender.send(new SendNotificationCommand(userId, type, "Título", "Mensaje", null, null));
    }

    @Nested
    @DisplayName("Envío")
    class Sending {

        @Test
        @DisplayName("guarda la notificación aunque el usuario no tenga celular registrado")
        void savesWithoutDevices() {
            send(ANA, NotificationTypeCode.SYSTEM_MESSAGE);

            assertEquals(1, notifications.size());
            assertTrue(push.sent.isEmpty());
        }

        @Test
        @DisplayName("manda push solo a los celulares activos del destinatario")
        void pushesToActiveDevices() {
            registration.register(ANA, TOKEN, "android");
            registration.register(LUIS, "ExponentPushToken[bbbbbbbbbbbbbbbbbbbb]", "ios");

            send(ANA, NotificationTypeCode.PAYMENT_CONFIRMED);

            assertEquals(1, push.sent.size());
            assertEquals(1, push.devices);
        }
    }

    @Nested
    @DisplayName("Interruptores de Configuración > Notificaciones")
    class Channels {

        @Test
        @DisplayName("con el push apagado la notificación queda en la bandeja y no llega al celular")
        void pushOffKeepsTheInboxOnly() {
            registration.register(ANA, TOKEN, "android");
            contacts.withChannels(ANA, false, true, true);

            send(ANA, NotificationTypeCode.PAYMENT_CONFIRMED);

            assertEquals(1, notifications.size());
            assertTrue(push.sent.isEmpty());
        }

        @Test
        @DisplayName("con las promociones apagadas el cupón no llega por push, lo demás sí")
        void promotionsOffSkipsOnlyTheCouponPush() {
            registration.register(ANA, TOKEN, "android");
            contacts.withChannels(ANA, true, true, false);

            send(ANA, NotificationTypeCode.PROMOTION_AVAILABLE);
            send(ANA, NotificationTypeCode.PAYMENT_CONFIRMED);

            assertEquals(2, notifications.size());
            assertEquals(1, push.sent.size());
        }

        @Test
        @DisplayName("si security-service no responde se envía el push igual")
        void unknownContactStillPushes() {
            registration.register(ANA, TOKEN, "android");

            send(ANA, NotificationTypeCode.PROMOTION_AVAILABLE);

            assertEquals(1, push.sent.size());
        }

        @Test
        @DisplayName("con el correo de recordatorios apagado el recordatorio no va al correo; lo demás sí")
        void reminderEmailOff() {
            contacts.with(ANA, "ana@gmail.com", "Ana", true).withChannels(ANA, true, false, true);
            EmailRecipients recipients = new EmailRecipients(contacts);

            SendNotificationCommand reminder = recipients.withEmail(
                    new SendNotificationCommand(ANA, NotificationTypeCode.BOOKING_REMINDER, "Recordatorio", "Mañana", null, null));
            SendNotificationCommand confirmed = recipients.withEmail(
                    new SendNotificationCommand(ANA, NotificationTypeCode.BOOKING_CONFIRMED, "Confirmada", "Listo", null, null));

            assertFalse(reminder.hasEmail());
            assertTrue(confirmed.hasEmail());
        }
    }

    @Nested
    @DisplayName("Bandeja")
    class Inbox {

        @Test
        @DisplayName("cada usuario ve solo sus notificaciones y cuenta sus no leídas")
        void listsOnlyOwn() {
            send(ANA, NotificationTypeCode.SYSTEM_MESSAGE);
            send(ANA, NotificationTypeCode.BOOKING_CANCELLED);
            send(LUIS, NotificationTypeCode.SYSTEM_MESSAGE);

            assertEquals(2, inbox.list(ANA, NotificationFilter.none(), 0, 20).totalElements());
            assertEquals(2, inbox.countUnread(ANA));
            assertEquals(1, inbox.countUnread(LUIS));
        }

        @Test
        @DisplayName("filtra por leídas y por categoría (pestaña)")
        void filtersByReadAndCategory() {
            Notification first = send(ANA, NotificationTypeCode.BOOKING_CANCELLED);
            send(ANA, NotificationTypeCode.PAYMENT_CONFIRMED);
            inbox.markRead(ANA, first.notificationId());

            assertEquals(1, inbox.list(ANA, new NotificationFilter(true, null, null, null), 0, 20).totalElements());
            assertEquals(1, inbox.list(ANA, new NotificationFilter(null, NotificationCategory.CANCELLATION, null, null),
                    0, 20).totalElements());
        }

        @Test
        @DisplayName("no deja tocar la notificación de otro usuario (responde como si no existiera)")
        void cannotTouchOthers() {
            Notification luis = send(LUIS, NotificationTypeCode.SYSTEM_MESSAGE);

            assertThrows(NotificationNotFoundException.class, () -> inbox.markRead(ANA, luis.notificationId()));
            assertThrows(NotificationNotFoundException.class, () -> inbox.delete(ANA, luis.notificationId()));
        }

        @Test
        @DisplayName("marcar todas leídas solo afecta las propias y devuelve cuántas cambió")
        void marksAllRead() {
            send(ANA, NotificationTypeCode.SYSTEM_MESSAGE);
            send(ANA, NotificationTypeCode.SYSTEM_MESSAGE);
            send(LUIS, NotificationTypeCode.SYSTEM_MESSAGE);

            assertEquals(2, inbox.markAllRead(ANA));
            assertEquals(0, inbox.countUnread(ANA));
            assertEquals(1, inbox.countUnread(LUIS));
        }

        @Test
        @DisplayName("una notificación borrada ya no aparece ni cuenta")
        void deletedDisappears() {
            Notification notification = send(ANA, NotificationTypeCode.SYSTEM_MESSAGE);

            inbox.delete(ANA, notification.notificationId());

            assertEquals(0, inbox.list(ANA, NotificationFilter.none(), 0, 20).totalElements());
            assertEquals(0, inbox.countUnread(ANA));
            assertThrows(NotificationNotFoundException.class, () -> inbox.markRead(ANA, notification.notificationId()));
        }

        @Test
        @DisplayName("rechaza páginas inválidas y rangos de fecha al revés")
        void validatesQuery() {
            assertThrows(InvalidValueException.class, () -> inbox.list(ANA, NotificationFilter.none(), -1, 20));
            assertThrows(InvalidValueException.class, () -> inbox.list(ANA, NotificationFilter.none(), 0, 101));
            assertThrows(InvalidValueException.class, () -> inbox.list(ANA,
                    new NotificationFilter(null, null, LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 1)), 0, 20));
        }
    }

    @Nested
    @DisplayName("Celulares")
    class Devices {

        @Test
        @DisplayName("registrar el mismo token dos veces no lo duplica")
        void registerIsIdempotent() {
            registration.register(ANA, TOKEN, "android");
            registration.register(ANA, TOKEN, "android");

            assertEquals(1, devices.size());
        }

        @Test
        @DisplayName("si otra cuenta inicia sesión en ese celular, las push pasan a esa cuenta")
        void reassignsDevice() {
            registration.register(ANA, TOKEN, "android");

            registration.register(LUIS, TOKEN, "android");

            assertTrue(devices.findActiveByUser(ANA).isEmpty());
            assertEquals(1, devices.findActiveByUser(LUIS).size());
            assertEquals(DevicePlatform.ANDROID, devices.findActiveByUser(LUIS).getFirst().platform());
        }

        @Test
        @DisplayName("solo el dueño puede quitar su celular")
        void onlyOwnerUnregisters() {
            registration.register(ANA, TOKEN, "android");

            registration.unregister(LUIS, TOKEN);
            assertEquals(1, devices.findActiveByUser(ANA).size());

            registration.unregister(ANA, TOKEN);
            assertTrue(devices.findActiveByUser(ANA).isEmpty());
        }
    }

    @Nested
    @DisplayName("Eventos de RabbitMQ")
    class Events {

        private DomainEventConsumerService consumer;
        private final DomainEventEnvelope paymentConfirmed = new DomainEventEnvelope("evt-42", "PaymentConfirmed",
                "pay-1", Instant.parse("2026-09-30T14:00:00Z"), 1,
                Map.of("paymentId", 90, "customerUserId", ANA, "amount", 45000));

        @BeforeEach
        void setUpConsumer() {
            // Ana tiene correo en security-service: así se ve qué tipos salen por correo y cuáles no
            contacts.with(ANA, "ana@gmail.com", "Ana", true);
            BookingReminderService reminders = new BookingReminderService(new Fakes.InMemoryReminders(), sender,
                    new ReminderSettings(List.of(Duration.ofHours(24), Duration.ofHours(1)), 100),
                    new EmailRecipients(contacts), clock);
            consumer = new DomainEventConsumerService(new InMemoryProcessedEvents(), sender, new EventNotificationFactory(),
                    reminders, new EmailRecipients(contacts),
                    new StaffNotificationService(new Fakes.InMemoryBookingTracking(), contacts,
                            new StaffNotificationFactory(), sender));
        }

        @Test
        @DisplayName("un evento crea la notificación del destinatario")
        void createsNotification() {
            consumer.handle(paymentConfirmed);

            List<Notification> items = inbox.list(ANA, NotificationFilter.none(), 0, 20).items();
            assertEquals(1, items.size());
            assertEquals(NotificationTypeCode.PAYMENT_CONFIRMED, items.getFirst().type());
            assertFalse(items.getFirst().isRead());
        }

        @Test
        @DisplayName("UserRegistered deja la bienvenida en la bandeja y la envía al correo del evento")
        void welcomeGoesToInboxAndEmail() {
            consumer.handle(new DomainEventEnvelope("evt-welcome", "UserRegistered", "20", Instant.now(), 1,
                    Map.of("userId", 20, "email", "ana@gmail.com", "firstName", "Ana")));

            Notification welcome = inbox.list(20, NotificationFilter.none(), 0, 20).items().getFirst();
            assertEquals(NotificationTypeCode.USER_WELCOME, welcome.type());
            assertEquals("¡Bienvenido a LavaRápido, Ana!", welcome.title());
            assertEquals(List.of("ana@gmail.com"), email.sentTo);
        }

        @Test
        @DisplayName("el avance del servicio se queda en la bandeja: no envía correo")
        void serviceProgressDoesNotEmail() {
            consumer.handle(new DomainEventEnvelope("evt-start", "ServiceStarted", "145", Instant.now(), 1,
                    Map.of("customerUserId", ANA, "bookingId", 145, "bookingCode", "RES-000145")));
            consumer.handle(new DomainEventEnvelope("evt-done", "ServiceCompleted", "145", Instant.now(), 1,
                    Map.of("customerUserId", ANA, "bookingId", 145, "bookingCode", "RES-000145")));

            assertEquals(2, notifications.size());
            assertTrue(email.sentTo.isEmpty());
        }

        @Test
        @DisplayName("el resultado de un pago (aprobado, rechazado, reembolsado) también va al correo del cliente")
        void paymentResultsAreEmailed() {
            consumer.handle(paymentConfirmed);
            consumer.handle(new DomainEventEnvelope("evt-43", "PaymentRejected", "pay-2", Instant.now(), 1,
                    Map.of("paymentId", 91, "customerUserId", ANA, "amount", 45000, "reason", "El monto no coincide")));
            consumer.handle(new DomainEventEnvelope("evt-44", "PaymentRefunded", "pay-1", Instant.now(), 1,
                    Map.of("paymentId", 90, "customerUserId", ANA, "amount", 45000)));

            assertEquals(List.of("ana@gmail.com", "ana@gmail.com", "ana@gmail.com"), email.sentTo);
        }

        @Test
        @DisplayName("el cupón desbloqueado con puntos va al correo; los puntos ganados se quedan en la bandeja")
        void unlockedCouponIsEmailedButPointsAreNot() {
            consumer.handle(new DomainEventEnvelope("evt-points", "LoyaltyPointsEarned", "145", Instant.now(), 1,
                    Map.of("customerUserId", ANA, "bookingId", 145, "bookingCode", "RES-000145", "points", 30,
                            "balance", 30, "unlockedPromotions",
                            List.of(Map.of("code", "LAVA20", "name", "Lavado 20%", "discountPercent", 20)))));

            List<Notification> items = inbox.list(ANA, NotificationFilter.none(), 0, 20).items();
            assertEquals(2, items.size());
            assertTrue(items.stream().anyMatch(n -> n.type() == NotificationTypeCode.LOYALTY_POINTS_EARNED));
            assertTrue(items.stream().anyMatch(n -> n.type() == NotificationTypeCode.PROMOTION_AVAILABLE
                    && n.category() == NotificationCategory.PROMOTION));
            assertEquals(List.of("ana@gmail.com"), email.sentTo);
        }

        @Test
        @DisplayName("con las promociones apagadas el cupón queda en la bandeja pero no va al correo")
        void couponEmailOffWithPromotionsOff() {
            contacts.withChannels(ANA, true, true, false);

            consumer.handle(new DomainEventEnvelope("evt-points-off", "LoyaltyPointsEarned", "146", Instant.now(), 1,
                    Map.of("customerUserId", ANA, "bookingId", 146, "bookingCode", "RES-000146", "points", 30,
                            "balance", 60, "unlockedPromotions",
                            List.of(Map.of("code", "LAVA20", "name", "Lavado 20%", "discountPercent", 20)))));

            assertEquals(2, inbox.list(ANA, NotificationFilter.none(), 0, 20).items().size());
            assertTrue(email.sentTo.isEmpty());
        }

        @Test
        @DisplayName("el mismo evento repetido no duplica la notificación (idempotencia)")
        void duplicatesAreIgnored() {
            consumer.handle(paymentConfirmed);
            consumer.handle(paymentConfirmed);

            assertEquals(1, notifications.size());
        }

        @Test
        @DisplayName("un evento sin eventId se ignora porque no se puede deduplicar")
        void withoutEventIdIsIgnored() {
            consumer.handle(new DomainEventEnvelope(null, "PaymentConfirmed", null, null, 1,
                    Map.of("customerUserId", ANA)));

            assertEquals(0, notifications.size());
        }
    }
}
