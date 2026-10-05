package com.lavarapido.notification.infrastructure.adapter.in.scheduling;

import com.lavarapido.notification.domain.port.in.BookingReminderUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Adaptador de entrada por tiempo: cada cierto rato envía los recordatorios vencidos.
 *
 * Con app.reminders.enabled=false no se envía nada (los recordatorios se siguen programando).
 * Pensado para una sola instancia del servicio: con varias habría que repartir el trabajo
 * (por ejemplo con un candado en la base) para no avisar dos veces.
 */
@Component
@ConditionalOnProperty(prefix = "app.reminders", name = "enabled", havingValue = "true", matchIfMissing = true)
class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

    private final BookingReminderUseCase reminders;

    ReminderScheduler(BookingReminderUseCase reminders) {
        this.reminders = reminders;
    }

    @Scheduled(fixedDelayString = "${app.reminders.check-interval:5m}", initialDelayString = "${app.reminders.initial-delay:30s}")
    void sendDueReminders() {
        try {
            reminders.sendDue();
        } catch (RuntimeException e) {
            // un fallo (base caída) no detiene la tarea: se reintenta en la siguiente vuelta
            log.error("Could not send the due booking reminders: {}", e.getMessage());
        }
    }
}
