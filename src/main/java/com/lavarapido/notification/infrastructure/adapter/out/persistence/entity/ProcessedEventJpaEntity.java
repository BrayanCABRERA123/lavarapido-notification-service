package com.lavarapido.notification.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Fila de notification.processed_event (tabla nueva, ADR-011): un evento ya atendido. */
@Entity
@Table(schema = "notification", name = "processed_event")
public class ProcessedEventJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "processed_event_id")
    private Long id;

    @Column(name = "event_id", nullable = false, length = 64, updatable = false)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 60, updatable = false)
    private String eventType;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ProcessedEventJpaEntity() {
    }

    public ProcessedEventJpaEntity(String eventId, String eventType, Instant processedAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.processedAt = processedAt;
        this.createdAt = processedAt;
    }
}
