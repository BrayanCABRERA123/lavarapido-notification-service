package com.lavarapido.notification.domain.model;

import com.lavarapido.notification.domain.exception.InvalidValueException;
import com.lavarapido.notification.domain.service.NotificationTexts;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Un recordatorio de reserva por enviar: "tu lavado es tal día a tal hora".
 *
 * Hay uno por reserva y por anticipación (por ejemplo 24 h y 1 h antes). Solo se programan los
 * que todavía caen en el futuro: si alguien reserva para dentro de 2 horas, el de 24 h no tiene
 * sentido y no se crea.
 */
public record BookingReminder(Long id, long bookingId, long userId, String bookingCode, Instant scheduledStart,
                              int leadMinutes, Instant remindAt, Instant sentAt) {

    public BookingReminder {
        if (bookingId <= 0 || userId <= 0) {
            throw new InvalidValueException("INVALID_REMINDER", "A reminder needs a booking and a user");
        }
        if (scheduledStart == null || remindAt == null || leadMinutes <= 0) {
            throw new InvalidValueException("INVALID_REMINDER", "A reminder needs the booking time and a lead");
        }
    }

    /**
     * Los recordatorios que hay que programar para una reserva: uno por anticipación, solo los
     * que todavía no pasaron.
     */
    public static List<BookingReminder> planFor(long bookingId, long userId, String bookingCode,
                                                Instant scheduledStart, List<Duration> leads, Instant now) {
        List<BookingReminder> planned = new ArrayList<>();
        for (Duration lead : leads) {
            Instant remindAt = scheduledStart.minus(lead);
            if (remindAt.isAfter(now)) {
                planned.add(new BookingReminder(null, bookingId, userId, bookingCode, scheduledStart,
                        (int) lead.toMinutes(), remindAt, null));
            }
        }
        return planned;
    }

    /** Ya pasó la cita: avisar ahora no sirve (por ejemplo, el servicio estuvo apagado). */
    public boolean isStale(Instant now) {
        return !scheduledStart.isAfter(now);
    }

    /** Plantillas del aviso (NotificationTexts): se escribe en el idioma de la cuenta del cliente. */
    public LocalizedText titleText() {
        return LocalizedText.of("reminder.title");
    }

    public LocalizedText messageText() {
        String code = bookingCode == null || bookingCode.isBlank() ? "" : " " + bookingCode;
        return LocalizedText.of("reminder.message").with("code", code).with("when", scheduledStart);
    }

    /** El aviso en español, el idioma por defecto. */
    public String title() {
        return NotificationTexts.defaults().render(titleText(), NotificationTexts.DEFAULT_LANGUAGE);
    }

    public String message() {
        return NotificationTexts.defaults().render(messageText(), NotificationTexts.DEFAULT_LANGUAGE);
    }
}
