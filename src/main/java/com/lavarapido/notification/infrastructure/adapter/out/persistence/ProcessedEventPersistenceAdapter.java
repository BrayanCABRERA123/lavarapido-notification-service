package com.lavarapido.notification.infrastructure.adapter.out.persistence;

import com.lavarapido.notification.domain.port.out.ProcessedEventRepository;
import com.lavarapido.notification.infrastructure.adapter.out.persistence.entity.ProcessedEventJpaEntity;
import com.lavarapido.notification.infrastructure.adapter.out.persistence.repository.ProcessedEventJpaRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
class ProcessedEventPersistenceAdapter implements ProcessedEventRepository {

    private final ProcessedEventJpaRepository events;
    private final Clock clock;

    ProcessedEventPersistenceAdapter(ProcessedEventJpaRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    @Override
    public boolean exists(String eventId) {
        return events.existsByEventId(eventId);
    }

    /**
     * Si dos copias del mismo evento llegan al tiempo, el índice único uq_processed_event_event
     * hace fallar a la segunda y su transacción (con sus notificaciones) se deshace.
     */
    @Override
    public void record(String eventId, String eventType) {
        events.save(new ProcessedEventJpaEntity(eventId, eventType, clock.instant()));
    }
}
