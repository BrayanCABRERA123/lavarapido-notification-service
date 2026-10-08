package com.lavarapido.notification.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Fila de notification.booking_tracking (migración 027): lo último que se supo de una reserva. */
@Entity
@Table(schema = "notification", name = "booking_tracking")
public class BookingTrackingJpaEntity {

    @Id
    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "booking_code", length = 20)
    private String bookingCode;

    @Column(name = "customer_user_id")
    private Long customerUserId;

    @Column(name = "operator_user_id")
    private Long operatorUserId;

    @Column(name = "scheduled_start")
    private Instant scheduledStart;

    @Column(name = "is_cancelled", nullable = false)
    private boolean cancelled;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BookingTrackingJpaEntity() {
    }

    public BookingTrackingJpaEntity(Long bookingId, String bookingCode, Long customerUserId, Long operatorUserId,
                                    Instant scheduledStart, boolean cancelled, Instant updatedAt) {
        this.bookingId = bookingId;
        this.bookingCode = bookingCode;
        this.customerUserId = customerUserId;
        this.operatorUserId = operatorUserId;
        this.scheduledStart = scheduledStart;
        this.cancelled = cancelled;
        this.updatedAt = updatedAt;
    }

    public Long getBookingId() {
        return bookingId;
    }

    public String getBookingCode() {
        return bookingCode;
    }

    public Long getCustomerUserId() {
        return customerUserId;
    }

    public Long getOperatorUserId() {
        return operatorUserId;
    }

    public Instant getScheduledStart() {
        return scheduledStart;
    }

    public boolean isCancelled() {
        return cancelled;
    }
}
