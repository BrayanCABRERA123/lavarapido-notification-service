package com.lavarapido.notification.application.fake;

import com.lavarapido.notification.domain.model.DeviceToken;
import com.lavarapido.notification.domain.model.Notification;
import com.lavarapido.notification.domain.model.NotificationFilter;
import com.lavarapido.notification.domain.model.PageResult;
import com.lavarapido.notification.domain.port.out.DeviceTokenRepository;
import com.lavarapido.notification.domain.port.out.EmailSender;
import com.lavarapido.notification.domain.port.out.NotificationRepository;
import com.lavarapido.notification.domain.port.out.ProcessedEventRepository;
import com.lavarapido.notification.domain.port.out.PushSender;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/** Repositorios en memoria para probar los casos de uso sin base de datos ni Spring. */
public final class Fakes {

    private Fakes() {
    }

    public static final class InMemoryNotifications implements NotificationRepository {

        private final Map<Long, Notification> rows = new LinkedHashMap<>();
        private final AtomicLong sequence = new AtomicLong();

        @Override
        public Notification save(Notification n) {
            long id = n.notificationId() == null ? sequence.incrementAndGet() : n.notificationId();
            Notification stored = Notification.restore(id, n.userId(), n.type(), n.title(), n.message(),
                    n.referenceEntity(), n.referenceId(), n.sentAt(), n.readAt(), n.deletedAt());
            rows.put(id, stored);
            return stored;
        }

        @Override
        public Optional<Notification> findActive(long id) {
            return Optional.ofNullable(rows.get(id)).filter(n -> !n.isDeleted()).map(this::copy);
        }

        @Override
        public PageResult<Notification> findActiveByUser(long userId, NotificationFilter filter, int page, int size) {
            ZoneId zone = ZoneId.of("America/Bogota");
            List<Notification> matching = rows.values().stream()
                    .filter(n -> n.userId() == userId && !n.isDeleted())
                    .filter(n -> filter.read() == null || n.isRead() == filter.read())
                    .filter(n -> filter.category() == null || n.category() == filter.category())
                    .filter(n -> filter.from() == null || !n.sentAt().isBefore(filter.from().atStartOfDay(zone).toInstant()))
                    .filter(n -> filter.to() == null || n.sentAt().isBefore(filter.to().plusDays(1).atStartOfDay(zone).toInstant()))
                    .sorted(Comparator.comparing(Notification::sentAt).reversed())
                    .toList();
            List<Notification> slice = matching.stream().skip((long) page * size).limit(size).toList();
            return new PageResult<>(slice, page, size, matching.size());
        }

        @Override
        public long countUnread(long userId) {
            return rows.values().stream().filter(n -> n.userId() == userId && !n.isDeleted() && !n.isRead()).count();
        }

        @Override
        public int markAllRead(long userId, Instant readAt) {
            int updated = 0;
            for (Notification n : new ArrayList<>(rows.values())) {
                if (n.userId() == userId && !n.isDeleted() && !n.isRead()) {
                    Notification copy = copy(n);
                    copy.markRead(readAt);
                    save(copy);
                    updated++;
                }
            }
            return updated;
        }

        public int size() {
            return rows.size();
        }

        private Notification copy(Notification n) {
            return Notification.restore(n.notificationId(), n.userId(), n.type(), n.title(), n.message(),
                    n.referenceEntity(), n.referenceId(), n.sentAt(), n.readAt(), n.deletedAt());
        }
    }

    public static final class InMemoryDevices implements DeviceTokenRepository {

        private final Map<String, DeviceToken> rows = new LinkedHashMap<>();
        private final AtomicLong sequence = new AtomicLong();

        @Override
        public DeviceToken save(DeviceToken device) {
            long id = device.deviceTokenId() == null
                    ? Optional.ofNullable(rows.get(device.token())).map(DeviceToken::deviceTokenId).orElseGet(sequence::incrementAndGet)
                    : device.deviceTokenId();
            DeviceToken stored = DeviceToken.restore(id, device.userId(), device.token(), device.platform(),
                    device.active(), device.lastSeenAt());
            rows.put(device.token(), stored);
            return stored;
        }

        @Override
        public Optional<DeviceToken> findByToken(String token) {
            return Optional.ofNullable(rows.get(token)).map(d -> DeviceToken.restore(d.deviceTokenId(), d.userId(),
                    d.token(), d.platform(), d.active(), d.lastSeenAt()));
        }

        @Override
        public List<DeviceToken> findActiveByUser(long userId) {
            return rows.values().stream().filter(d -> d.userId() == userId && d.active()).toList();
        }

        @Override
        public void deactivate(String token) {
            findByToken(token).ifPresent(d -> {
                d.deactivate();
                save(d);
            });
        }

        public int size() {
            return rows.size();
        }
    }

    public static final class InMemoryProcessedEvents implements ProcessedEventRepository {

        private final Set<String> ids = new HashSet<>();

        @Override
        public boolean exists(String eventId) {
            return ids.contains(eventId);
        }

        @Override
        public void record(String eventId, String eventType) {
            ids.add(eventId);
        }
    }

    /** Guarda a qué correos se habría enviado cada notificación. */
    public static final class RecordingEmailSender implements EmailSender {

        public final List<String> sentTo = new ArrayList<>();

        @Override
        public void send(Notification notification, String toAddress, String recipientName) {
            sentTo.add(toAddress);
        }
    }

    /** Guarda qué push se habrían enviado. */
    public static final class RecordingPushSender implements PushSender {

        public final List<Notification> sent = new ArrayList<>();
        public int devices;

        @Override
        public void send(Notification notification, List<DeviceToken> targets) {
            sent.add(notification);
            devices += targets.size();
        }
    }
}
