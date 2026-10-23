package com.lavarapido.notification.application.usecase;

import com.lavarapido.notification.application.fake.Fakes.InMemoryBookingTracking;
import com.lavarapido.notification.application.fake.Fakes.InMemoryContacts;
import com.lavarapido.notification.application.fake.Fakes.InMemoryDevices;
import com.lavarapido.notification.application.fake.Fakes.InMemoryNotifications;
import com.lavarapido.notification.application.fake.Fakes.RecordingEmailSender;
import com.lavarapido.notification.application.fake.Fakes.RecordingPushSender;
import com.lavarapido.notification.domain.model.NotificationFilter;
import com.lavarapido.notification.domain.model.NotificationTypeCode;
import com.lavarapido.notification.domain.model.TrackedBooking;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Aviso al administrador: reserva que empieza pronto y sigue sin operario")
class UnassignedBookingAlertTest {

    private static final Instant NOW = Instant.parse("2026-10-09T15:00:00Z");
    private static final long ADMIN_1 = 900, ADMIN_2 = 901, OPERATOR = 50;

    private InMemoryBookingTracking tracking;
    private InMemoryNotifications notifications;
    private RecordingEmailSender email;
    private UnassignedBookingAlertService service;

    @BeforeEach
    void setUp() {
        tracking = new InMemoryBookingTracking();
        notifications = new InMemoryNotifications();
        email = new RecordingEmailSender();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        InMemoryContacts contacts = new InMemoryContacts().withAdmins(ADMIN_1, ADMIN_2);
        NotificationSendingService sender = new NotificationSendingService(notifications, new InMemoryDevices(),
                new RecordingPushSender(), email, contacts, clock);
        service = new UnassignedBookingAlertService(tracking, contacts, sender, clock, Duration.ofHours(2));
    }

    private void track(long id, Long operator, Instant start, boolean cancelled) {
        TrackedBooking booking = TrackedBooking.confirmed(id, "RES-" + id, 7L, start).assignedTo(operator);
        tracking.save(cancelled ? booking.cancel() : booking);
    }

    @Test
    @DisplayName("una reserva sin operario que empieza en 1 hora avisa a cada administrador (sin correo)")
    void alertsEveryAdmin() {
        track(1, null, NOW.plus(Duration.ofHours(1)), false);

        assertEquals(2, service.alertDue());

        assertEquals(NotificationTypeCode.ADMIN_BOOKING_UNASSIGNED, notifications.findActiveByUser(ADMIN_1, NotificationFilter.none(), 0, 20).items().getFirst().type());
        assertTrue(notifications.findActiveByUser(ADMIN_2, NotificationFilter.none(), 0, 20).items().getFirst().message().contains("RES-1"));
        assertTrue(email.sentTo.isEmpty());
    }

    @Test
    @DisplayName("no avisa si ya tiene operario, si está cancelada o si todavía faltan más de 2 horas")
    void ignoresAssignedCancelledOrFarAway() {
        track(2, OPERATOR, NOW.plus(Duration.ofHours(1)), false);
        track(3, null, NOW.plus(Duration.ofHours(1)), true);
        track(4, null, NOW.plus(Duration.ofHours(5)), false);

        assertEquals(0, service.alertDue());
        assertEquals(0, notifications.size());
    }

    @Test
    @DisplayName("también avisa la que ya debía empezar hace menos de una hora; las más viejas no")
    void alertsJustStartedButNotOldOnes() {
        track(6, null, NOW.minus(Duration.ofMinutes(20)), false);
        track(7, null, NOW.minus(Duration.ofDays(7)), false);

        assertEquals(2, service.alertDue());
        assertTrue(notifications.findActiveByUser(ADMIN_1, NotificationFilter.none(), 0, 20).items().getFirst().message()
                .contains("RES-6 ya debía empezar"));
    }

    @Test
    @DisplayName("cada reserva se avisa una sola vez")
    void alertsOnlyOnce() {
        track(5, null, NOW.plus(Duration.ofMinutes(90)), false);

        service.alertDue();
        assertEquals(0, service.alertDue());
        assertEquals(2, notifications.size());
    }
}
