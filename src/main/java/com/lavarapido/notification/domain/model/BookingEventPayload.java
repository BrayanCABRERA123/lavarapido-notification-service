package com.lavarapido.notification.domain.model;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;

/**
 * Lo que los recordatorios necesitan de BookingConfirmed / BookingCancelled (payload que publica
 * booking-service). Si falta algo, no se programa nada: el evento igual genera su notificación.
 */
public record BookingEventPayload(long bookingId, Long customerUserId, String bookingCode, Instant scheduledStart) {

    public static Optional<BookingEventPayload> from(DomainEventEnvelope event) {
        Map<String, Object> payload = event.payload();
        Long bookingId = number(payload.get("bookingId"));
        if (bookingId == null || bookingId <= 0) {
            return Optional.empty();
        }
        return Optional.of(new BookingEventPayload(bookingId, number(payload.get("customerUserId")),
                text(payload.get("bookingCode")), instant(payload.get("scheduledStart"))));
    }

    /** Tiene todo lo necesario para programar recordatorios. */
    public boolean canBeReminded() {
        return customerUserId != null && customerUserId > 0 && scheduledStart != null;
    }

    private static Long number(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && text.matches("\\d{1,18}")) {
            return Long.parseLong(text);
        }
        return null;
    }

    private static String text(Object value) {
        return value instanceof String text && !text.isBlank() ? text.strip() : null;
    }

    private static Instant instant(Object value) {
        if (value instanceof String text) {
            try {
                return Instant.parse(text);
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
        return null;
    }
}
