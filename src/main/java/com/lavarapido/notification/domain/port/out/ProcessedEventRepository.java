package com.lavarapido.notification.domain.port.out;

/**
 * Eventos ya procesados (tabla notification.processed_event, ADR-011): la entrega es "al menos
 * una vez", así que un evento repetido no debe crear la notificación dos veces.
 */
public interface ProcessedEventRepository {

    boolean exists(String eventId);

    void record(String eventId, String eventType);
}
