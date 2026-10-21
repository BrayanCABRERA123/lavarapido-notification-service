package com.lavarapido.notification.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Sobre común de todos los mensajes de carwash.events (cross-cutting.md, sección 7).
 * eventId es la llave de idempotencia: el mismo evento puede llegar más de una vez.
 */
public record DomainEventEnvelope(String eventId, String eventType, String aggregateId, Instant occurredAt,
                                  int version, Map<String, Object> payload) {

    public DomainEventEnvelope {
        // un campo en null (ej. customerUserId cuando el evento no lo conoce) se trata como ausente:
        // Map.copyOf no admite nulos y el mensaje terminaba en la DLQ
        payload = payload == null ? Map.of() : payload.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
