package com.lavarapido.notification.infrastructure.adapter.in.scheduling;

import com.lavarapido.notification.application.delivery.PendingDeliveryDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * OPS-05: cada cierto rato reintenta las push y los correos que fallaron (o que no alcanzaron a salir
 * porque el servicio se reinició justo después del commit). Pensado para una sola instancia, como los
 * recordatorios; aun así cada entrega se toma con claim y no sale dos veces a la vez.
 */
@Component
class DeliveryRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(DeliveryRetryScheduler.class);

    private final PendingDeliveryDispatcher dispatcher;

    DeliveryRetryScheduler(PendingDeliveryDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelayString = "${app.delivery.check-interval:1m}", initialDelayString = "${app.delivery.initial-delay:45s}")
    void retryPendingDeliveries() {
        try {
            int delivered = dispatcher.dispatchDue();
            if (delivered > 0) {
                log.info("Retried pending deliveries: {} delivered", delivered);
            }
        } catch (RuntimeException e) {
            // la base caída no detiene la tarea: se intenta en la siguiente vuelta
            log.error("Could not retry the pending deliveries: {}", e.getMessage());
        }
    }
}
