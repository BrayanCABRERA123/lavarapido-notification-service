package com.lavarapido.notification.domain.model;

import java.time.Instant;

/**
 * Lo último que notification-service supo de una reserva por los eventos (tabla
 * notification.booking_tracking, migración 027). Sirve para avisar al personal: saber si un
 * BookingConfirmed es nuevo o una reprogramación, y quién es el operario asignado.
 */
public record TrackedBooking(long bookingId, String bookingCode, Long customerUserId, Long operatorUserId,
                             Instant scheduledStart, boolean cancelled) {

    public static TrackedBooking confirmed(long bookingId, String bookingCode, Long customerUserId,
                                           Instant scheduledStart) {
        return new TrackedBooking(bookingId, bookingCode, customerUserId, null, scheduledStart, false);
    }

    /** Reprogramada (o confirmada otra vez): conserva el operario asignado. */
    public TrackedBooking rescheduled(String code, Instant start) {
        return new TrackedBooking(bookingId, code == null ? bookingCode : code, customerUserId, operatorUserId,
                start == null ? scheduledStart : start, false);
    }

    public TrackedBooking assignedTo(Long operator) {
        return new TrackedBooking(bookingId, bookingCode, customerUserId, operator, scheduledStart, cancelled);
    }

    public TrackedBooking cancel() {
        return new TrackedBooking(bookingId, bookingCode, customerUserId, operatorUserId, scheduledStart, true);
    }

    /** La hora cambió respecto a la que ya se conocía (si no se conocía, no cuenta como cambio). */
    public boolean startChangesTo(Instant start) {
        return scheduledStart != null && start != null && !scheduledStart.equals(start);
    }
}
