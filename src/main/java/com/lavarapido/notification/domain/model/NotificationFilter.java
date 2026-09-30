package com.lavarapido.notification.domain.model;

import java.time.LocalDate;

/**
 * Filtros de la bandeja, los mismos que tienen las pantallas: leídas/no leídas, categoría
 * (pestaña) y rango de fechas. null = sin filtrar por ese campo.
 */
public record NotificationFilter(Boolean read, NotificationCategory category, LocalDate from, LocalDate to) {

    public static NotificationFilter none() {
        return new NotificationFilter(null, null, null, null);
    }
}
