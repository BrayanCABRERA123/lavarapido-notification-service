package com.lavarapido.notification.domain.model;

import com.lavarapido.notification.domain.exception.InvalidValueException;

import java.time.Instant;
import java.util.Objects;

/**
 * Una notificación enviada a un usuario (tabla notification.notification).
 *
 * Guarda el título y el mensaje ya escritos, no una plantilla: si mañana cambia la redacción de
 * "Pago aprobado", las notificaciones de ayer deben seguir diciendo lo que dijeron (06-data).
 *
 * Reglas que protege:
 * - is_read y read_at van juntos (ck_notification_read): leída siempre tiene fecha, no leída no.
 * - borrar es lógico (deleted_at): ningún servicio tiene permiso DELETE en la base.
 */
public final class Notification {

    public static final int MAX_TITLE_LENGTH = 120;
    public static final int MAX_MESSAGE_LENGTH = 500;
    public static final int MAX_REFERENCE_ENTITY_LENGTH = 40;

    private final Long notificationId;
    private final long userId;
    private final NotificationTypeCode type;
    private final String title;
    private final String message;
    private final String referenceEntity;
    private final Long referenceId;
    private final Instant sentAt;
    private Instant readAt;
    private Instant deletedAt;

    private Notification(Long notificationId, long userId, NotificationTypeCode type, String title, String message,
                         String referenceEntity, Long referenceId, Instant sentAt, Instant readAt, Instant deletedAt) {
        this.notificationId = notificationId;
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.message = message;
        this.referenceEntity = referenceEntity;
        this.referenceId = referenceId;
        this.sentAt = sentAt;
        this.readAt = readAt;
        this.deletedAt = deletedAt;
    }

    /** Notificación nueva, sin leer. La referencia (ej. booking 145) es opcional pero va completa. */
    public static Notification create(long userId, NotificationTypeCode type, String title, String message,
                                      String referenceEntity, Long referenceId, Instant now) {
        if (userId <= 0) {
            throw new InvalidValueException("INVALID_RECIPIENT", "The recipient user id must be positive");
        }
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(now, "now");
        String cleanReference = blankToNull(referenceEntity);
        if ((cleanReference == null) != (referenceId == null)) {
            throw new InvalidValueException("INVALID_REFERENCE",
                    "referenceEntity and referenceId must be sent together or not at all");
        }
        if (cleanReference != null && cleanReference.length() > MAX_REFERENCE_ENTITY_LENGTH) {
            throw new InvalidValueException("INVALID_REFERENCE", "referenceEntity is too long");
        }
        return new Notification(null, userId, type,
                requireText(title, MAX_TITLE_LENGTH, "INVALID_TITLE"),
                requireText(message, MAX_MESSAGE_LENGTH, "INVALID_MESSAGE"),
                cleanReference, referenceId, now, null, null);
    }

    /** Reconstruye una fila ya guardada (lo usa el adaptador de persistencia). */
    public static Notification restore(long notificationId, long userId, NotificationTypeCode type, String title,
                                       String message, String referenceEntity, Long referenceId, Instant sentAt,
                                       Instant readAt, Instant deletedAt) {
        return new Notification(notificationId, userId, type, title, message, referenceEntity, referenceId,
                sentAt, readAt, deletedAt);
    }

    /** Marcar como leída otra vez no cambia la fecha en que se leyó por primera vez. */
    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = Objects.requireNonNull(now, "now");
        }
    }

    public void markUnread() {
        readAt = null;
    }

    /** Borrado lógico; borrarla dos veces conserva la primera fecha. */
    public void delete(Instant now) {
        if (deletedAt == null) {
            deletedAt = Objects.requireNonNull(now, "now");
        }
    }

    public boolean belongsTo(long user) {
        return userId == user;
    }

    public boolean isRead() {
        return readAt != null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public NotificationCategory category() {
        return type.category();
    }

    public Long notificationId() {
        return notificationId;
    }

    public long userId() {
        return userId;
    }

    public NotificationTypeCode type() {
        return type;
    }

    public String title() {
        return title;
    }

    public String message() {
        return message;
    }

    public String referenceEntity() {
        return referenceEntity;
    }

    public Long referenceId() {
        return referenceId;
    }

    public Instant sentAt() {
        return sentAt;
    }

    public Instant readAt() {
        return readAt;
    }

    public Instant deletedAt() {
        return deletedAt;
    }

    private static String requireText(String value, int maxLength, String code) {
        String clean = value == null ? "" : value.strip();
        if (clean.isEmpty() || clean.length() > maxLength) {
            throw new InvalidValueException(code, "Text must have between 1 and " + maxLength + " characters");
        }
        return clean;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
