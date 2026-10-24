package com.lavarapido.notification.application.delivery;

import com.lavarapido.notification.application.fake.Fakes.InMemoryDevices;
import com.lavarapido.notification.application.fake.Fakes.InMemoryNotifications;
import com.lavarapido.notification.application.fake.Fakes.RecordingEmailSender;
import com.lavarapido.notification.domain.port.out.PushSender;
import com.lavarapido.notification.domain.model.DevicePlatform;
import com.lavarapido.notification.domain.model.DeviceToken;
import com.lavarapido.notification.domain.model.Notification;
import com.lavarapido.notification.domain.model.NotificationTypeCode;
import com.lavarapido.notification.domain.model.PendingDelivery;
import com.lavarapido.notification.domain.port.out.PendingDeliveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Entregas pendientes de push y correo (OPS-05)")
class PendingDeliveriesTest {

    private static final Instant NOW = Instant.parse("2026-10-23T15:00:00Z");

    private final InMemoryPending pending = new InMemoryPending();
    private final InMemoryNotifications notifications = new InMemoryNotifications();
    private final InMemoryDevices devices = new InMemoryDevices();
    private final FlakyPush push = new FlakyPush();
    private final RecordingEmailSender email = new RecordingEmailSender();
    private MutableClock clock;
    private PendingDeliveryDispatcher dispatcher;
    private Notification saved;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(NOW);
        dispatcher = new PendingDeliveryDispatcher(pending, notifications, devices, push, email, clock, 3,
                Duration.ofMinutes(1), 100);
        saved = notifications.save(Notification.create(7L, NotificationTypeCode.BOOKING_CONFIRMED, "Reserva confirmada",
                "Tu reserva quedó confirmada", "booking", 50L, NOW));
        devices.save(DeviceToken.register(7L, "ExponentPushToken[aaaaaaaaaaaaaaaaaaaa]", DevicePlatform.ANDROID, NOW));
    }

    private List<DeliveryCommand> pushAndEmail() {
        return List.of(new PushDeliveryCommand(saved, devices.findActiveByUser(7L), push),
                new EmailDeliveryCommand(saved, "ana@gmail.com", "Ana", "es", email));
    }

    @Test
    @DisplayName("se guardan en la transacción y salen solo después del commit")
    void theyLeaveAfterTheCommit() {
        PersistentDeliveryOutbox outbox = new PersistentDeliveryOutbox(pending, dispatcher, clock);
        TransactionSynchronizationManager.initSynchronization();
        try {
            outbox.submit(saved, pushAndEmail());
            // todavía dentro de la transacción: guardadas, sin enviar
            assertEquals(2, pending.rows.size());
            assertTrue(push.sent.isEmpty());
            assertTrue(email.sentTo.isEmpty());

            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        assertEquals(1, push.sent.size());
        assertEquals(List.of("ana@gmail.com"), email.sentTo);
        assertTrue(pending.rows.values().stream().allMatch(row -> row.deliveredAt != null));
        // la dirección del correo no se queda guardada después de entregarlo
        assertTrue(pending.rows.values().stream().allMatch(row -> row.delivery.emailAddress() == null));
    }

    @Test
    @DisplayName("si la transacción se deshace, no sale nada (el evento reintentado no duplica la push)")
    void aRolledBackTransactionSendsNothing() {
        PersistentDeliveryOutbox outbox = new PersistentDeliveryOutbox(pending, dispatcher, clock);
        TransactionSynchronizationManager.initSynchronization();
        try {
            outbox.submit(saved, pushAndEmail());
            // rollback: no hay afterCommit (y en la base las filas también se deshacen)
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        assertTrue(push.sent.isEmpty());
        assertTrue(email.sentTo.isEmpty());
    }

    @Test
    @DisplayName("una push que falla se reintenta con espera creciente y se abandona en el tope de intentos")
    void failuresAreRetriedUpToTheLimit() {
        push.failuresLeft = 10;
        long id = pending.addAll(List.of(PendingDelivery.push(saved.notificationId())), NOW).getFirst();

        assertFalse(dispatcher.dispatch(id));
        assertEquals(NOW.plus(Duration.ofMinutes(1)), pending.rows.get(id).nextAttemptAt);
        // antes de la hora del reintento la tarea no la toma
        assertEquals(0, dispatcher.dispatchDue());

        clock.now = NOW.plus(Duration.ofMinutes(1));
        dispatcher.dispatchDue();
        assertEquals(clock.now.plus(Duration.ofMinutes(2)), pending.rows.get(id).nextAttemptAt);

        clock.now = clock.now.plus(Duration.ofMinutes(2));
        dispatcher.dispatchDue();
        // tercer intento fallido: se abandona
        assertNotNull(pending.rows.get(id).failedAt);
        clock.now = clock.now.plus(Duration.ofHours(2));
        assertEquals(0, dispatcher.dispatchDue());
        assertEquals(3, push.attempts);
    }

    @Test
    @DisplayName("una push que falló una vez sale en el reintento, una sola vez")
    void aRetriedPushLeavesOnce() {
        push.failuresLeft = 1;
        long id = pending.addAll(List.of(PendingDelivery.push(saved.notificationId())), NOW).getFirst();

        dispatcher.dispatch(id);
        clock.now = NOW.plus(Duration.ofMinutes(1));
        assertEquals(1, dispatcher.dispatchDue());
        // ya entregada: ni la tarea ni otro hilo la vuelven a tomar
        assertEquals(0, dispatcher.dispatchDue());
        assertFalse(dispatcher.dispatch(id));
        assertEquals(1, push.sent.size());
    }

    @Test
    @DisplayName("la push se arma con los celulares activos al momento de enviarla; sin celulares no hay nada que enviar")
    void pushUsesTheCurrentDevices() {
        long id = pending.addAll(List.of(PendingDelivery.push(saved.notificationId())), NOW).getFirst();
        devices.deactivate("ExponentPushToken[aaaaaaaaaaaaaaaaaaaa]");

        assertTrue(dispatcher.dispatch(id));
        assertTrue(push.sent.isEmpty());
    }

    // ------------------------------------------------------------------ dobles

    /** Push que falla las primeras veces (Expo caído) y anota las que sí salen. */
    private static final class FlakyPush implements PushSender {

        final List<Notification> sent = new ArrayList<>();
        int failuresLeft;
        int attempts;

        @Override
        public void send(Notification notification, List<DeviceToken> targets) {
            attempts++;
            if (failuresLeft > 0) {
                failuresLeft--;
                throw new IllegalStateException("Expo did not answer");
            }
            sent.add(notification);
        }
    }

    private static final class MutableClock extends Clock {

        Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    /** Misma regla que la base: claim solo toma la que no salió, no se abandonó y ya le toca. */
    private static final class InMemoryPending implements PendingDeliveryRepository {

        static final class Row {
            PendingDelivery delivery;
            Instant nextAttemptAt;
            Instant deliveredAt;
            Instant failedAt;
        }

        final Map<Long, Row> rows = new LinkedHashMap<>();
        private long sequence;

        @Override
        public List<Long> addAll(List<PendingDelivery> deliveries, Instant now) {
            List<Long> ids = new ArrayList<>();
            for (PendingDelivery delivery : deliveries) {
                long id = ++sequence;
                Row row = new Row();
                row.delivery = new PendingDelivery(id, delivery.notificationId(), delivery.channel(),
                        delivery.emailAddress(), delivery.recipientName(), delivery.language(), 0);
                row.nextAttemptAt = now;
                rows.put(id, row);
                ids.add(id);
            }
            return ids;
        }

        @Override
        public List<Long> findDue(Instant now, int limit) {
            return rows.entrySet().stream()
                    .filter(e -> open(e.getValue()) && !e.getValue().nextAttemptAt.isAfter(now))
                    .map(Map.Entry::getKey).limit(limit).toList();
        }

        @Override
        public Optional<PendingDelivery> claim(long id, Instant now, Instant leaseUntil) {
            Row row = rows.get(id);
            if (row == null || !open(row) || row.nextAttemptAt.isAfter(now)) {
                return Optional.empty();
            }
            PendingDelivery d = row.delivery;
            row.delivery = new PendingDelivery(d.id(), d.notificationId(), d.channel(), d.emailAddress(),
                    d.recipientName(), d.language(), d.attempts() + 1);
            row.nextAttemptAt = leaseUntil;
            return Optional.of(row.delivery);
        }

        @Override
        public void markDelivered(long id, Instant at) {
            Row row = rows.get(id);
            row.deliveredAt = at;
            forget(row);
        }

        @Override
        public void markRetry(long id, Instant nextAttemptAt, String error) {
            rows.get(id).nextAttemptAt = nextAttemptAt;
        }

        @Override
        public void markFailed(long id, Instant at, String error) {
            Row row = rows.get(id);
            row.failedAt = at;
            forget(row);
        }

        private static boolean open(Row row) {
            return row.deliveredAt == null && row.failedAt == null;
        }

        private static void forget(Row row) {
            PendingDelivery d = row.delivery;
            row.delivery = new PendingDelivery(d.id(), d.notificationId(), d.channel(), null, null, d.language(),
                    d.attempts());
        }
    }
}
