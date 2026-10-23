package com.lavarapido.notification.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Recordatorio de reserva")
class BookingReminderTest {

    private static final List<Duration> LEADS = List.of(Duration.ofHours(24), Duration.ofHours(1));
    /** 1 de octubre de 2026, 09:00 en Bogotá. */
    private static final Instant START = Instant.parse("2026-10-01T14:00:00Z");

    @Test
    @DisplayName("con tiempo de sobra se programan los dos: 24 h y 1 h antes")
    void plansBothLeads() {
        List<BookingReminder> planned = BookingReminder.planFor(1L, 7L, "RES-000001", START, LEADS,
                Instant.parse("2026-09-29T12:00:00Z"));

        assertEquals(2, planned.size());
        assertEquals(Instant.parse("2026-09-30T14:00:00Z"), planned.get(0).remindAt());
        assertEquals(Instant.parse("2026-10-01T13:00:00Z"), planned.get(1).remindAt());
    }

    @Test
    @DisplayName("si reserva 3 horas antes, solo queda el de 1 hora")
    void skipsPastLeads() {
        List<BookingReminder> planned = BookingReminder.planFor(1L, 7L, "RES-000001", START, LEADS,
                Instant.parse("2026-10-01T11:00:00Z"));

        assertEquals(1, planned.size());
        assertEquals(60, planned.getFirst().leadMinutes());
    }

    @Test
    @DisplayName("el mensaje lleva el código y la hora de Colombia")
    void messageUsesColombianTime() {
        BookingReminder reminder = BookingReminder.planFor(1L, 7L, "RES-000001", START, LEADS,
                Instant.parse("2026-09-29T12:00:00Z")).getFirst();

        assertEquals("Tu reserva RES-000001 es el 01/10/2026 a las 09:00. Te esperamos en la sede.", reminder.message());
    }

    @Test
    @DisplayName("después de la hora de la cita el recordatorio ya no sirve")
    void staleAfterTheBooking() {
        BookingReminder reminder = new BookingReminder(1L, 1L, 7L, null, START, 60, START.minusSeconds(3600), null);

        assertFalse(reminder.isStale(START.minusSeconds(60)));
        assertTrue(reminder.isStale(START.plusSeconds(60)));
    }
}
