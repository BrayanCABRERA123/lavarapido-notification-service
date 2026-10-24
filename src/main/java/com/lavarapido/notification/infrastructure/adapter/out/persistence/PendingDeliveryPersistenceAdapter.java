package com.lavarapido.notification.infrastructure.adapter.out.persistence;

import com.lavarapido.notification.domain.model.PendingDelivery;
import com.lavarapido.notification.domain.port.out.PendingDeliveryRepository;
import com.lavarapido.notification.infrastructure.adapter.out.persistence.entity.PendingDeliveryJpaEntity;
import com.lavarapido.notification.infrastructure.adapter.out.persistence.repository.PendingDeliveryJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Cada método va en su propia transacción corta (o se une a la de quien llama, como addAll dentro de
 * la de la notificación): el envío a Expo o al SMTP nunca ocurre con una transacción abierta.
 */
@Component
class PendingDeliveryPersistenceAdapter implements PendingDeliveryRepository {

    private final PendingDeliveryJpaRepository deliveries;

    PendingDeliveryPersistenceAdapter(PendingDeliveryJpaRepository deliveries) {
        this.deliveries = deliveries;
    }

    @Override
    @Transactional
    public List<Long> addAll(List<PendingDelivery> list, Instant now) {
        return list.stream()
                .map(d -> deliveries.save(new PendingDeliveryJpaEntity(d.notificationId(), d.channel().name(),
                        d.emailAddress(), d.recipientName(), d.language(), now)).getId())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findDue(Instant now, int limit) {
        return deliveries.findDueIds(now, PageRequest.of(0, limit));
    }

    @Override
    @Transactional
    public Optional<PendingDelivery> claim(long id, Instant now, Instant leaseUntil) {
        if (deliveries.claim(id, now, leaseUntil) == 0) {
            return Optional.empty();
        }
        return deliveries.findById(id).map(PendingDeliveryPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional
    public void markDelivered(long id, Instant at) {
        deliveries.markDelivered(id, at);
    }

    @Override
    @Transactional
    public void markRetry(long id, Instant nextAttemptAt, String error) {
        deliveries.markRetry(id, nextAttemptAt, error);
    }

    @Override
    @Transactional
    public void markFailed(long id, Instant at, String error) {
        deliveries.markFailed(id, at, error);
    }

    private static PendingDelivery toDomain(PendingDeliveryJpaEntity row) {
        return new PendingDelivery(row.getId(), row.getNotificationId(), PendingDelivery.Channel.valueOf(row.getChannel()),
                row.getEmailAddress(), row.getRecipientName(), row.getLanguage(), row.getAttempts());
    }
}
