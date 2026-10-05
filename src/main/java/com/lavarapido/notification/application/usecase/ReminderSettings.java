package com.lavarapido.notification.application.usecase;

import java.time.Duration;
import java.util.List;

/**
 * Parámetros de los recordatorios (application.yml, app.reminders.*).
 *
 * @param leads     con cuánta anticipación se avisa (por defecto 24 h y 1 h antes)
 * @param batchSize cuántos recordatorios se envían como máximo en cada revisión
 */
public record ReminderSettings(List<Duration> leads, int batchSize) {

    public ReminderSettings {
        leads = leads == null ? List.of() : List.copyOf(leads);
        if (leads.stream().anyMatch(lead -> lead.isNegative() || lead.isZero())) {
            throw new IllegalStateException("app.reminders.leads must be positive durations");
        }
        if (batchSize <= 0) {
            throw new IllegalStateException("app.reminders.batch-size must be greater than zero");
        }
    }
}
