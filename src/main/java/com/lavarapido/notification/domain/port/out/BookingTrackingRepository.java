package com.lavarapido.notification.domain.port.out;

import java.time.Instant;
import java.util.List;
import com.lavarapido.notification.domain.model.TrackedBooking;

import java.util.Optional;

/** Puerto hacia notification.booking_tracking (migración 027). */
public interface BookingTrackingRepository {

    Optional<TrackedBooking> find(long bookingId);

    /** Crea o reemplaza la foto de la reserva (y borra la marca del aviso de "sin operario"). */
    void save(TrackedBooking booking);

    /** Reservas no canceladas, sin operario, que empiezan entre from y to y todavía no tienen el aviso. */
    List<TrackedBooking> findUnassignedStartingBetween(Instant from, Instant to);

    /** Deja constancia de que ya se avisó a los administradores, para no repetirlo. */
    void markUnassignedAlertSent(long bookingId, Instant at);
}
