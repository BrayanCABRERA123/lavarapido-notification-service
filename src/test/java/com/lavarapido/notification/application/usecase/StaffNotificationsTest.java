package com.lavarapido.notification.application.usecase;

import com.lavarapido.notification.application.fake.Fakes.InMemoryBookingTracking;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Avisos para el operario y el administrador (RF-018)")
class StaffNotificationsTest {

    private static final long CLIENT = 7;
    private static final long ADMIN = 1;
    private static final long JUAN = 50;
    private static final long CAMILO = 51;
    private static final String START = "2026-10-10T14:00:00Z";   // 10/10/2026 09:00 en Bogotá
    private static final String LATER = "2026-10-11T15:30:00Z";   // 11/10/2026 10:30 en Bogotá

    private InMemoryNotifications notifications;
    private RecordingEmailSender email;
    private InMemoryBookingTracking tracking;
    private DomainEventConsumerService consumer;
    private int sequence;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-08T12:00:00Z"), ZoneOffset.UTC);
        notifications = new InMemoryNotifications();
        email = new RecordingEmailSender();
        tracking = new InMemoryBookingTracking();
        InMemoryContacts contacts = new InMemoryContacts()
                .with(CLIENT, "cliente@gmail.com", "Ana", true)
                .with(JUAN, "juan@gmail.com", "Juan", true)
                .with(ADMIN, "admin@gmail.com", "Admin", true)
                .withAdmins(ADMIN);
        NotificationSendingService sender = new NotificationSendingService(notifications, new InMemoryDevices(),
                new RecordingPushSender(), email, contacts, clock);
        BookingReminderService reminders = new BookingReminderService(new InMemoryReminders(), sender,
                new ReminderSettings(List.of(Duration.ofHours(24)), 100), new EmailRecipients(contacts), clock);
        consumer = new DomainEventConsumerService(new InMemoryProcessedEvents(), sender, new EventNotificationFactory(),
                reminders, new EmailRecipients(contacts),
                new StaffNotificationService(tracking, contacts, new StaffNotificationFactory(), sender));
    }

    private void event(String type, Map<String, Object> payload) {
        Map<String, Object> full = new HashMap<>(Map.of("bookingId", 145, "bookingCode", "RES-000145",
                "customerUserId", CLIENT, "scheduledStart", START));
        full.putAll(payload);
        consumer.handle(new DomainEventEnvelope("evt-" + (++sequence), type, "145",
                Instant.parse("2026-10-08T12:00:00Z"), 1, full));
    }

    private List<Notification> inboxOf(long userId) {
        return new NotificationInboxService(notifications, Clock.systemUTC())
                .list(userId, NotificationFilter.none(), 0, 50).items();
    }

    private List<NotificationTypeCode> typesOf(long userId) {
        return inboxOf(userId).stream().map(Notification::type).toList();
    }

    // cuántas de cada tipo tiene la bandeja (sin depender del orden: en la prueba todas tienen la misma hora)
    private Map<NotificationTypeCode, Long> countsOf(long userId) {
        return inboxOf(userId).stream().collect(Collectors.groupingBy(Notification::type, Collectors.counting()));
    }

    // la única notificación de ese tipo en la bandeja
    private Notification only(long userId, NotificationTypeCode type) {
        List<Notification> found = inboxOf(userId).stream().filter(n -> n.type() == type).toList();
        assertEquals(1, found.size(), "notificaciones " + type + ": " + typesOf(userId));
        return found.getFirst();
    }

    @Test
    @DisplayName("una reserva nueva le avisa al administrador, con la fecha en hora de Colombia")
    void newBookingNotifiesTheAdmin() {
        event("BookingConfirmed", Map.of());

        Notification notice = only(ADMIN, NotificationTypeCode.ADMIN_BOOKING_CREATED);
        assertEquals(NotificationCategory.CONFIRMATION, notice.category());
        assertTrue(notice.message().contains("RES-000145"), notice.message());
        assertTrue(notice.message().contains("10/10/2026 a las 09:00"), notice.message());
        assertEquals("booking", notice.referenceEntity());
        assertEquals(145L, notice.referenceId());
    }

    @Test
    @DisplayName("reprogramar avisa al administrador y al operario asignado, no como reserva nueva")
    void rescheduleNotifiesAdminAndOperator() {
        event("BookingConfirmed", Map.of());
        event("OperatorAssigned", Map.of("operatorUserId", JUAN));
        event("BookingConfirmed", Map.of("scheduledStart", LATER));

        assertEquals(Map.of(NotificationTypeCode.ADMIN_BOOKING_CREATED, 1L, NotificationTypeCode.ADMIN_BOOKING_RESCHEDULED, 1L),
                countsOf(ADMIN));
        Notification operatorNotice = only(JUAN, NotificationTypeCode.OPERATOR_SERVICE_RESCHEDULED);
        assertTrue(operatorNotice.message().contains("11/10/2026 a las 10:30"), operatorNotice.message());
    }

    @Test
    @DisplayName("confirmar otra vez a la misma hora (solo cambiaron servicios) no avisa al personal")
    void sameTimeIsNotAReschedule() {
        event("BookingConfirmed", Map.of());
        event("OperatorAssigned", Map.of("operatorUserId", JUAN));
        event("BookingConfirmed", Map.of());

        assertEquals(List.of(NotificationTypeCode.ADMIN_BOOKING_CREATED), typesOf(ADMIN));
        assertEquals(List.of(NotificationTypeCode.OPERATOR_ASSIGNED), typesOf(JUAN));
    }

    @Test
    @DisplayName("una cancelación avisa al administrador (con el motivo) y al operario asignado")
    void cancellationNotifiesAdminAndOperator() {
        event("BookingConfirmed", Map.of());
        event("OperatorAssigned", Map.of("operatorUserId", JUAN));
        // booking-service manda el nombre del motivo, no el código
        event("BookingCancelled", Map.of("reason", "El cliente la canceló"));

        Notification adminNotice = only(ADMIN, NotificationTypeCode.ADMIN_BOOKING_CANCELLED);
        assertTrue(adminNotice.message().contains("Motivo: El cliente la canceló."), adminNotice.message());
        Notification operatorNotice = only(JUAN, NotificationTypeCode.OPERATOR_SERVICE_CANCELLED);
        assertEquals(NotificationCategory.CANCELLATION, operatorNotice.category());
    }

    @Test
    @DisplayName("cancelar una reserva sin operario solo avisa al administrador")
    void cancellationWithoutOperator() {
        event("BookingConfirmed", Map.of());
        event("BookingCancelled", Map.of());

        only(ADMIN, NotificationTypeCode.ADMIN_BOOKING_CANCELLED);
        assertTrue(typesOf(JUAN).isEmpty());
    }

    @Test
    @DisplayName("reasignar avisa al operario anterior; reasignar al mismo no avisa nada")
    void reassignmentNotifiesThePreviousOperator() {
        event("BookingConfirmed", Map.of());
        event("OperatorAssigned", Map.of("operatorUserId", JUAN));
        event("OperatorAssigned", Map.of("operatorUserId", JUAN));
        event("OperatorAssigned", Map.of("operatorUserId", CAMILO));

        assertEquals(Map.of(NotificationTypeCode.OPERATOR_ASSIGNED, 2L, NotificationTypeCode.OPERATOR_SERVICE_UNASSIGNED, 1L),
                countsOf(JUAN));
        assertEquals(List.of(NotificationTypeCode.OPERATOR_ASSIGNED), typesOf(CAMILO));
        assertEquals(CAMILO, tracking.rows.get(145L).operatorUserId());
    }

    @Test
    @DisplayName("una reserva de antes de la tabla también avisa al operario si luego se cancela")
    void bookingFromBeforeTheTableIsTrackedOnAssignment() {
        event("OperatorAssigned", Map.of("operatorUserId", JUAN));
        event("BookingCancelled", Map.of("reason", "CUSTOMER_REQUEST"));

        only(JUAN, NotificationTypeCode.OPERATOR_SERVICE_CANCELLED);
    }

    @Test
    @DisplayName("los avisos del personal nunca salen por correo")
    void staffNoticesAreNotEmailed() {
        event("BookingConfirmed", Map.of());
        event("OperatorAssigned", Map.of("operatorUserId", JUAN));
        event("BookingConfirmed", Map.of("scheduledStart", LATER));
        event("BookingCancelled", Map.of("reason", "CUSTOMER_REQUEST"));

        assertFalse(email.sentTo.contains("admin@gmail.com"), email.sentTo.toString());
        assertFalse(email.sentTo.contains("juan@gmail.com"), email.sentTo.toString());
        // el cliente sí sigue recibiendo sus correos de reserva
        assertTrue(email.sentTo.contains("cliente@gmail.com"), email.sentTo.toString());
    }

    @Test
    @DisplayName("sin administradores (o sin security-service) no se crea nada ni falla")
    void noAdmins() {
        Clock clock = Clock.systemUTC();
        InMemoryContacts withoutAdmins = new InMemoryContacts();
        NotificationSendingService sender = new NotificationSendingService(notifications, new InMemoryDevices(),
                new RecordingPushSender(), email, withoutAdmins, clock);
        StaffNotificationService staff = new StaffNotificationService(tracking, withoutAdmins,
                new StaffNotificationFactory(), sender);

        int created = staff.handle(new DomainEventEnvelope("evt-x", "BookingConfirmed", "145", Instant.now(), 1,
                Map.of("bookingId", 145, "bookingCode", "RES-000145", "scheduledStart", START)));

        assertEquals(0, created);
        assertTrue(tracking.rows.containsKey(145L));
    }
}
