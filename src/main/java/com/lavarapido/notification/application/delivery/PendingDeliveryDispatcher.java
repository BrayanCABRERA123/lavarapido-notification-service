package com.lavarapido.notification.application.delivery;

import com.lavarapido.notification.domain.model.DeviceToken;
import com.lavarapido.notification.domain.model.Notification;
import com.lavarapido.notification.domain.model.PendingDelivery;
import com.lavarapido.notification.domain.port.out.DeviceTokenRepository;
import com.lavarapido.notification.domain.port.out.EmailSender;
import com.lavarapido.notification.domain.port.out.NotificationRepository;
import com.lavarapido.notification.domain.port.out.PendingDeliveryRepository;
import com.lavarapido.notification.domain.port.out.PushSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Envía las entregas pendientes (OPS-05): toma cada una (claim, así nunca sale dos veces a la vez),
 * arma el comando del canal con datos frescos (los celulares activos de hoy) y lo ejecuta. Si sale, la
 * marca entregada; si falla, la deja para más tarde con espera creciente (retry-interval, 2x, 4x...) y,
 * al llegar al tope de intentos (max-attempts), la abandona y queda en el log.
 */
@Service
public class PendingDeliveryDispatcher {

    private static final Logger log = LoggerFactory.getLogger(PendingDeliveryDispatcher.class);
    // mientras se envía nadie más la toma; si el servicio se cae a mitad, se reintenta pasado este tiempo
    private static final Duration LEASE = Duration.ofMinutes(5);
    private static final Duration MAX_WAIT = Duration.ofHours(1);
    private static final int MAX_ERROR = 300;

    private final PendingDeliveryRepository pending;
    private final NotificationRepository notifications;
    private final DeviceTokenRepository devices;
    private final PushSender pushSender;
    private final EmailSender emailSender;
    private final Clock clock;
    private final int maxAttempts;
    private final Duration retryInterval;
    private final int batchSize;

    public PendingDeliveryDispatcher(PendingDeliveryRepository pending, NotificationRepository notifications,
                                     DeviceTokenRepository devices, PushSender pushSender, EmailSender emailSender,
                                     Clock clock,
                                     @Value("${app.delivery.max-attempts:5}") int maxAttempts,
                                     @Value("${app.delivery.retry-interval:1m}") Duration retryInterval,
                                     @Value("${app.delivery.batch-size:100}") int batchSize) {
        this.pending = pending;
        this.notifications = notifications;
        this.devices = devices;
        this.pushSender = pushSender;
        this.emailSender = emailSender;
        this.clock = clock;
        this.maxAttempts = maxAttempts;
        this.retryInterval = retryInterval;
        this.batchSize = batchSize;
    }

    /** Después del commit, en otro hilo: la petición o el evento no esperan a Expo ni al SMTP. */
    @Async
    public void dispatchLater(List<Long> ids) {
        ids.forEach(this::dispatchSafely);
    }

    /** La tarea programada: lo que falló y ya toca reintentar. @return cuántas salieron */
    public int dispatchDue() {
        int delivered = 0;
        for (long id : pending.findDue(clock.instant(), batchSize)) {
            if (dispatchSafely(id)) {
                delivered++;
            }
        }
        return delivered;
    }

    private boolean dispatchSafely(long id) {
        try {
            return dispatch(id);
        } catch (RuntimeException e) {
            // la base no respondió al tomarla o marcarla: la tarea programada la vuelve a intentar
            log.warn("Pending delivery {} could not be processed: {}", id, e.getMessage());
            return false;
        }
    }

    /** @return true si salió (o no había a quién enviarla) */
    boolean dispatch(long id) {
        Instant now = clock.instant();
        Optional<PendingDelivery> claimed = pending.claim(id, now, now.plus(LEASE));
        if (claimed.isEmpty()) {
            return false;
        }
        PendingDelivery delivery = claimed.get();
        Optional<Notification> notification = notifications.findActive(delivery.notificationId());
        if (notification.isEmpty()) {
            pending.markFailed(id, now, "The notification no longer exists");
            return false;
        }
        try {
            commandFor(delivery, notification.get()).ifPresent(DeliveryCommand::execute);
            pending.markDelivered(id, clock.instant());
            return true;
        } catch (RuntimeException e) {
            String error = shorten(e.getMessage());
            if (delivery.attempts() >= maxAttempts) {
                pending.markFailed(id, clock.instant(), error);
                log.error("Delivery by {} of notification {} abandoned after {} attempts: {}", delivery.channel(),
                        delivery.notificationId(), delivery.attempts(), error);
            } else {
                Instant next = clock.instant().plus(waitAfter(delivery.attempts()));
                pending.markRetry(id, next, error);
                log.warn("Delivery by {} of notification {} failed (attempt {}), retry at {}: {}", delivery.channel(),
                        delivery.notificationId(), delivery.attempts(), next, error);
            }
            return false;
        }
    }

    /** El comando del canal; vacío si ya no hay a quién enviarla (sin celulares activos). */
    private Optional<DeliveryCommand> commandFor(PendingDelivery delivery, Notification notification) {
        return switch (delivery.channel()) {
            case PUSH -> {
                List<DeviceToken> targets = devices.findActiveByUser(notification.userId());
                yield targets.isEmpty() ? Optional.empty()
                        : Optional.of(new PushDeliveryCommand(notification, targets, pushSender));
            }
            case EMAIL -> Optional.of(new EmailDeliveryCommand(notification, delivery.emailAddress(),
                    delivery.recipientName(), delivery.language(), emailSender));
        };
    }

    /** Espera creciente: 1x, 2x, 4x... el intervalo, hasta una hora. */
    Duration waitAfter(int attempts) {
        Duration wait = retryInterval.multipliedBy(1L << Math.min(Math.max(attempts - 1, 0), 10));
        return wait.compareTo(MAX_WAIT) > 0 ? MAX_WAIT : wait;
    }

    private static String shorten(String message) {
        String text = message == null ? "error" : message;
        return text.length() > MAX_ERROR ? text.substring(0, MAX_ERROR) : text;
    }
}
