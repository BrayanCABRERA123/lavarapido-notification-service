package com.lavarapido.notification.application.usecase;

import com.lavarapido.notification.application.fake.Fakes.InMemoryContacts;
import com.lavarapido.notification.application.fake.Fakes.InMemoryDevices;
import com.lavarapido.notification.application.fake.Fakes.InMemoryNotifications;
import com.lavarapido.notification.application.fake.Fakes.InMemoryProcessedEvents;
import com.lavarapido.notification.application.fake.Fakes.InMemoryReminders;
import com.lavarapido.notification.application.fake.Fakes.RecordingEmailSender;
import com.lavarapido.notification.application.fake.Fakes.RecordingPushSender;
import com.lavarapido.notification.domain.model.DomainEventEnvelope;
import com.lavarapido.notification.domain.model.Notification;
import com.lavarapido.notification.domain.model.NotificationCategory;
import com.lavarapido.notification.domain.model.NotificationFilter;
import com.lavarapido.notification.domain.model.NotificationTypeCode;
import com.lavarapido.notification.domain.service.EventNotificationFactory;
import com.lavarapido.notification.domain.service.StaffNotificationFactory;
import com.lavarapido.notification.application.fake.Fakes.InMemoryBookingTracking;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Recordatorios de reserva (casos de uso)")
class BookingReminderServiceTest {

    private static final long ANA = 7;
    private static final String START = "2026-10-01T14:00:00Z";   // 09:00 en Bogotá
    private static final ReminderSettings SETTINGS =
            new ReminderSettings(List.of(Duration.ofHours(24), Duration.ofHours(1)), 100);

    private InMemoryReminders reminders;
    private InMemoryNotifications notifications;
    private NotificationSendingService sender;
    private RecordingEmailSender email;
    private InMemoryContacts contacts;

    private BookingReminderService at(String now) {
        Clock clock = Clock.fixed(Instant.parse(now), ZoneOffset.UTC);
        sender = new NotificationSendingService(notifications, new InMemoryDevices(), new RecordingPushSender(),
                email, contacts, clock);
        return new BookingReminderService(reminders, sender, SETTINGS, new EmailRecipients(contacts), clock);
    }

    @BeforeEach
    void setUp() {
        reminders = new InMemoryReminders();
        notifications = new InMemoryNotifications();
        email = new RecordingEmailSender();
        contacts = new InMemoryContacts().with(ANA, "ana@gmail.com", "Ana", true);
    }

    private List<Notification> inboxOf(long userId) {
        return new NotificationInboxService(notifications, Clock.systemUTC())
                .list(userId, NotificationFilter.none(), 0, 20).items();
    }

    @Test
    @DisplayName("una reserva confirmada deja dos recordatorios pendientes")
    void schedulesTwo() {
        at("2026-09-29T12:00:00Z").schedule(1L, ANA, "RES-000001", Instant.parse(START));

        assertEquals(2, reminders.pending().size());
    }

    @Test
    @DisplayName("antes de la hora no envía nada; a la hora envía el de 24 h a la bandeja")
    void sendsWhenDue() {
        at("2026-09-29T12:00:00Z").schedule(1L, ANA, "RES-000001", Instant.parse(START));

        assertEquals(0, at("2026-09-30T13:59:00Z").sendDue());
        assertEquals(1, at("2026-09-30T14:00:00Z").sendDue());

        Notification sent = inboxOf(ANA).getFirst();
        assertEquals(NotificationTypeCode.BOOKING_REMINDER, sent.type());
        assertEquals(NotificationCategory.REMINDER, sent.category());
        assertEquals("booking", sent.referenceEntity());
        assertEquals(1L, sent.referenceId());
    }

    @Test
    @DisplayName("un recordatorio enviado no se repite")
    void doesNotRepeat() {
        at("2026-09-29T12:00:00Z").schedule(1L, ANA, "RES-000001", Instant.parse(START));
        at("2026-09-30T14:00:00Z").sendDue();

        assertEquals(0, at("2026-09-30T14:05:00Z").sendDue());
        assertEquals(1, inboxOf(ANA).size());
    }

    @Test
    @DisplayName("una reserva cancelada ya no recuerda nada")
    void cancelStopsReminders() {
        at("2026-09-29T12:00:00Z").schedule(1L, ANA, "RES-000001", Instant.parse(START));
        at("2026-09-29T13:00:00Z").cancel(1L);

        assertEquals(0, at("2026-10-01T13:30:00Z").sendDue());
        assertTrue(reminders.pending().isEmpty());
    }

    @Test
    @DisplayName("reprogramar mueve los recordatorios a la nueva hora")
    void rescheduleMovesThem() {
        at("2026-09-29T12:00:00Z").schedule(1L, ANA, "RES-000001", Instant.parse(START));
        at("2026-09-29T12:30:00Z").schedule(1L, ANA, "RES-000001", Instant.parse("2026-10-02T14:00:00Z"));

        assertEquals(2, reminders.pending().size());
        assertEquals(0, at("2026-09-30T14:00:00Z").sendDue());
        assertEquals(1, at("2026-10-01T14:00:00Z").sendDue());
    }

    @Test
    @DisplayName("si la cita ya pasó (servicio apagado) se descarta sin avisar")
    void staleIsDiscarded() {
        at("2026-09-29T12:00:00Z").schedule(1L, ANA, "RES-000001", Instant.parse(START));

        assertEquals(0, at("2026-10-01T16:00:00Z").sendDue());
        assertTrue(inboxOf(ANA).isEmpty());
        assertTrue(reminders.pending().isEmpty());
    }

    @Test
    @DisplayName("BookingConfirmed y BookingCancelled programan y cancelan desde RabbitMQ")
    void eventsDriveTheReminders() {
        BookingReminderService service = at("2026-09-29T12:00:00Z");
        DomainEventConsumerService consumer = new DomainEventConsumerService(new InMemoryProcessedEvents(), sender,
                new EventNotificationFactory(), service, new EmailRecipients(contacts),
                new StaffNotificationService(new InMemoryBookingTracking(), contacts, new StaffNotificationFactory(), sender));

        consumer.handle(new DomainEventEnvelope("evt-1", "BookingConfirmed", "1", Instant.parse("2026-09-29T12:00:00Z"), 1,
                Map.of("bookingId", 1, "bookingCode", "RES-000001", "customerUserId", ANA, "scheduledStart", START)));
        assertEquals(2, reminders.pending().size());

        consumer.handle(new DomainEventEnvelope("evt-2", "BookingCancelled", "1", Instant.parse("2026-09-29T12:10:00Z"), 1,
                Map.of("bookingId", 1, "bookingCode", "RES-000001", "customerUserId", ANA, "scheduledStart", START)));
        assertTrue(reminders.pending().isEmpty());
    }

    @Test
    @DisplayName("un BookingConfirmed sin cliente o sin hora no programa nada (y no falla)")
    void incompleteEventIsIgnored() {
        BookingReminderService service = at("2026-09-29T12:00:00Z");
        DomainEventConsumerService consumer = new DomainEventConsumerService(new InMemoryProcessedEvents(), sender,
                new EventNotificationFactory(), service, new EmailRecipients(contacts),
                new StaffNotificationService(new InMemoryBookingTracking(), contacts, new StaffNotificationFactory(), sender));

        consumer.handle(new DomainEventEnvelope("evt-3", "BookingConfirmed", "2", Instant.parse("2026-09-29T12:00:00Z"), 1,
                Map.of("bookingId", 2, "bookingCode", "RES-000002")));

        assertTrue(reminders.pending().isEmpty());
    }

    @Test
    @DisplayName("el recordatorio también llega al correo actual del cliente")
    void reminderIsEmailed() {
        at("2026-09-29T12:00:00Z").schedule(1L, ANA, "RES-000001", Instant.parse(START));
        at("2026-09-30T14:00:00Z").sendDue();

        assertEquals(List.of("ana@gmail.com"), email.sentTo);
    }

    @Test
    @DisplayName("sin correo (cuenta desactivada o security caído) igual llega a la bandeja")
    void withoutContactStillReachesTheInbox() {
        contacts.with(ANA, "ana@gmail.com", "Ana", false);
        at("2026-09-29T12:00:00Z").schedule(1L, ANA, "RES-000001", Instant.parse(START));
        at("2026-09-30T14:00:00Z").sendDue();

        assertTrue(email.sentTo.isEmpty());
        assertEquals(1, inboxOf(ANA).size());
    }

    @Test
    @DisplayName("la reserva confirmada va a la bandeja y al correo")
    void confirmedBookingIsEmailed() {
        BookingReminderService service = at("2026-09-29T12:00:00Z");
        DomainEventConsumerService consumer = new DomainEventConsumerService(new InMemoryProcessedEvents(), sender,
                new EventNotificationFactory(), service, new EmailRecipients(contacts),
                new StaffNotificationService(new InMemoryBookingTracking(), contacts, new StaffNotificationFactory(), sender));

        consumer.handle(new DomainEventEnvelope("evt-9", "BookingConfirmed", "1", Instant.parse("2026-09-29T12:00:00Z"), 1,
                Map.of("bookingId", 1, "bookingCode", "RES-000001", "customerUserId", ANA, "scheduledStart", START)));

        assertEquals(List.of("ana@gmail.com"), email.sentTo);
        assertEquals(NotificationTypeCode.BOOKING_CONFIRMED, inboxOf(ANA).getFirst().type());
    }
}
