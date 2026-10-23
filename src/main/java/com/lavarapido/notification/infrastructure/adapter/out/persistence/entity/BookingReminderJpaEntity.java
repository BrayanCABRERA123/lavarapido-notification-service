package com.lavarapido.notification.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Fila de notification.booking_reminder (tabla nueva, migración 019). Una reserva cancelada
 * deja sus recordatorios con deleted_at; uno ya enviado tiene sent_at.
 */
@Entity
@Table(schema = "notification", name = "booking_reminder")
public class BookingReminderJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "booking_reminder_id")
    private Long id;

    @Column(name = "booking_id", nullable = false, updatable = false)
    private Long bookingId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "booking_code", length = 20)
    private String bookingCode;

    @Column(name = "scheduled_start", nullable = false)
    private Instant scheduledStart;

    @Column(name = "lead_minutes", nullable = false, updatable = false)
    private Integer leadMinutes;

    @Column(name = "remind_at", nullable = false)
    private Instant remindAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected BookingReminderJpaEntity() {
    }

    public BookingReminderJpaEntity(Long bookingId, Integer leadMinutes, Instant createdAt) {
        this.bookingId = bookingId;
        this.leadMinutes = leadMinutes;
        this.createdAt = createdAt;
    }

    /** Programa (o mueve) el aviso y lo deja pendiente otra vez. */
    public void schedule(Long userId, String bookingCode, Instant scheduledStart, Instant remindAt, Instant now) {
        this.userId = userId;
        this.bookingCode = bookingCode;
        this.scheduledStart = scheduledStart;
        this.remindAt = remindAt;
        this.sentAt = null;
        this.deletedAt = null;
        this.updatedAt = now;
    }

    public void cancel(Instant now) {
        this.deletedAt = now;
        this.updatedAt = now;
    }

    public void markSent(Instant now) {
        this.sentAt = now;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getBookingId() {
        return bookingId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getBookingCode() {
        return bookingCode;
    }

    public Instant getScheduledStart() {
        return scheduledStart;
    }

    public Integer getLeadMinutes() {
        return leadMinutes;
    }

    public Instant getRemindAt() {
        return remindAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
