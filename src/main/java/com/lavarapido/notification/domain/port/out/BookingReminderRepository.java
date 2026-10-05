package com.lavarapido.notification.domain.port.out;

import com.lavarapido.notification.domain.model.BookingReminder;

import java.time.Instant;
import java.util.List;

/** Recordatorios de reserva programados (notification.booking_reminder). */
public interface BookingReminderRepository {

    /**
     * Deja programados exactamente estos recordatorios para la reserva: crea o mueve los que
     * vienen (y los vuelve a dejar pendientes) y cancela los demás que tenga esa reserva.
     */
    void replaceForBooking(long bookingId, List<BookingReminder> reminders, Instant now);

    /** Cancela todos los recordatorios pendientes de la reserva. */
    void cancelForBooking(long bookingId, Instant now);

    /** Pendientes cuya hora de aviso ya llegó, los más antiguos primero. */
    List<BookingReminder> findDue(Instant now, int limit);

    void markSent(long reminderId, Instant sentAt);
}
