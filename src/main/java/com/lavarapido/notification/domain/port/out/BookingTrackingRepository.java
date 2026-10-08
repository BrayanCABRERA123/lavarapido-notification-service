package com.lavarapido.notification.domain.port.out;

import com.lavarapido.notification.domain.model.TrackedBooking;

import java.util.Optional;

/** Puerto hacia notification.booking_tracking (migración 027). */
public interface BookingTrackingRepository {

    Optional<TrackedBooking> find(long bookingId);

    /** Crea o reemplaza la foto de la reserva. */
    void save(TrackedBooking booking);
}
