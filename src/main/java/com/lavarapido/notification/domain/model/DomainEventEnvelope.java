package com.lavarapido.notification.domain.model;

import java.time.Instant;
import java.util.Map;

/**
 * Sobre común de todos los mensajes de carwash.events (cross-cutting.md, sección 7).
 * eventId es la llave de idempotencia: el mismo evento puede llegar más de una vez.
 */
public record DomainEventEnvelope(String eventId, String eventType, String aggregateId, Instant occurredAt,
                                  int version, Map<String, Object> payload) {

    public DomainEventEnvelope {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
