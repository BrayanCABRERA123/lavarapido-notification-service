package com.lavarapido.notification.domain.port.in;

import java.time.Instant;

/** Recordatorios de reserva: se programan con los eventos de booking y se envían a su hora. */
public interface BookingReminderUseCase {

    /** BookingConfirmed (nueva o reprogramada): programa o mueve los recordatorios. */
    void schedule(long bookingId, long userId, String bookingCode, Instant scheduledStart);

    /** BookingCancelled: los recordatorios pendientes ya no se envían. */
    void cancel(long bookingId);

    /** Envía los recordatorios cuya hora ya llegó y devuelve cuántos envió. */
    int sendDue();
}
