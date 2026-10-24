package com.lavarapido.notification.application.delivery;

import com.lavarapido.notification.domain.model.Notification;
import com.lavarapido.notification.domain.port.out.PendingDeliveryRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.util.List;

/**
 * OPS-05 (E-14): las entregas se guardan en notification.pending_delivery dentro de la transacción de
 * la notificación y salen DESPUÉS del commit (en otro hilo, PendingDeliveryDispatcher). Si la
 * transacción se deshace (por ejemplo, el evento falla y RabbitMQ lo reintenta), no queda nada que
 * enviar: la push y el correo no salen antes de tiempo ni duplicados. Lo que falla lo reintenta
 * DeliveryRetryScheduler hasta el tope de intentos.
 */
@Component
public class PersistentDeliveryOutbox implements DeliveryOutbox {

    private final PendingDeliveryRepository pending;
    private final PendingDeliveryDispatcher dispatcher;
    private final Clock clock;

    public PersistentDeliveryOutbox(PendingDeliveryRepository pending, PendingDeliveryDispatcher dispatcher, Clock clock) {
        this.pending = pending;
        this.dispatcher = dispatcher;
        this.clock = clock;
    }

    @Override
    public void submit(Notification saved, List<DeliveryCommand> deliveries) {
        if (deliveries.isEmpty()) {
            return;
        }
        List<Long> ids = pending.addAll(deliveries.stream().map(DeliveryCommand::toPending).toList(), clock.instant());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatcher.dispatchLater(ids);
                }
            });
        } else {
            dispatcher.dispatchLater(ids);
        }
    }
}
