package com.lavarapido.notification.domain.model;

import com.lavarapido.notification.domain.exception.InvalidValueException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Notification")
class NotificationTest {

    private static final Instant NOW = Instant.parse("2026-09-30T15:00:00Z");
    private static final Instant LATER = NOW.plusSeconds(60);

    private static Notification create() {
        return Notification.create(7, NotificationTypeCode.PAYMENT_CONFIRMED, "  Pago aprobado ", "Aprobamos tu pago.",
                "payment", 45L, NOW);
    }

    @Test
    @DisplayName("nace sin leer, con el texto limpio y la categoría de su tipo")
    void createsUnread() {
        Notification notification = create();

        assertFalse(notification.isRead());
        assertNull(notification.readAt());
        assertEquals("Pago aprobado", notification.title());
        assertEquals(NotificationCategory.CONFIRMATION, notification.category());
        assertEquals(NOW, notification.sentAt());
    }

    @Test
    @DisplayName("marcar leída guarda la primera fecha de lectura y no la cambia al repetir")
    void markReadKeepsFirstDate() {
        Notification notification = create();

        notification.markRead(NOW);
        notification.markRead(LATER);

        assertTrue(notification.isRead());
        assertEquals(NOW, notification.readAt());
    }

    @Test
    @DisplayName("marcar no leída borra la fecha de lectura (is_read y read_at van juntos)")
    void markUnreadClearsDate() {
        Notification notification = create();
        notification.markRead(NOW);

        notification.markUnread();

        assertFalse(notification.isRead());
        assertNull(notification.readAt());
    }

    @Test
    @DisplayName("borrar es lógico y conserva la primera fecha")
    void deleteIsLogical() {
        Notification notification = create();

        notification.delete(NOW);
        notification.delete(LATER);

        assertTrue(notification.isDeleted());
        assertEquals(NOW, notification.deletedAt());
    }

    @Test
    @DisplayName("rechaza título vacío o de más de 120 caracteres")
    void rejectsInvalidTitle() {
        assertThrows(InvalidValueException.class, () -> Notification.create(7, NotificationTypeCode.SYSTEM_MESSAGE,
                "   ", "mensaje", null, null, NOW));
        assertThrows(InvalidValueException.class, () -> Notification.create(7, NotificationTypeCode.SYSTEM_MESSAGE,
                "x".repeat(121), "mensaje", null, null, NOW));
    }

    @Test
    @DisplayName("rechaza mensaje de más de 500 caracteres")
    void rejectsLongMessage() {
        assertThrows(InvalidValueException.class, () -> Notification.create(7, NotificationTypeCode.SYSTEM_MESSAGE,
                "Título", "x".repeat(501), null, null, NOW));
    }

    @Test
    @DisplayName("la referencia va completa (entidad e id) o no va")
    void referenceMustBeComplete() {
        assertThrows(InvalidValueException.class, () -> Notification.create(7, NotificationTypeCode.BOOKING_CREATED,
                "Título", "Mensaje", "booking", null, NOW));
        assertThrows(InvalidValueException.class, () -> Notification.create(7, NotificationTypeCode.BOOKING_CREATED,
                "Título", "Mensaje", null, 145L, NOW));
    }

    @Test
    @DisplayName("rechaza un destinatario inválido")
    void rejectsInvalidRecipient() {
        assertThrows(InvalidValueException.class, () -> Notification.create(0, NotificationTypeCode.SYSTEM_MESSAGE,
                "Título", "Mensaje", null, null, NOW));
    }

    @Test
    @DisplayName("solo pertenece a su destinatario")
    void belongsToRecipient() {
        Notification notification = create();

        assertTrue(notification.belongsTo(7));
        assertFalse(notification.belongsTo(8));
    }
}
