package com.lavarapido.notification.domain.model;

/**
 * Grupo con el que las pantallas ordenan las notificaciones (pestañas de la web y el móvil).
 * No se guarda: sale del tipo, así cambiar la agrupación no obliga a migrar datos.
 */
public enum NotificationCategory {
    REMINDER,
    PROMOTION,
    CONFIRMATION,
    CANCELLATION,
    MESSAGE,
    SYSTEM
}
