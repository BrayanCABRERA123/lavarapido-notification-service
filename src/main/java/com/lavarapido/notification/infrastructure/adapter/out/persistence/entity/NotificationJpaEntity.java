package com.lavarapido.notification.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * La fila de notification.notification. is_read se guarda derivado de read_at, así nunca rompe
 * el CHECK ck_notification_read. updated_at y row_version los maneja el trigger de la base.
 */
@Entity
@Table(schema = "notification", name = "notification")
public class NotificationJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "notification_type_id", nullable = false, updatable = false)
    private Short notificationTypeId;

    @Column(name = "title", nullable = false, length = 120, updatable = false)
    private String title;

    @Column(name = "message", nullable = false, length = 500, updatable = false)
    private String message;

    @Column(name = "reference_entity", length = 40, updatable = false)
    private String referenceEntity;

    @Column(name = "reference_id", updatable = false)
    private Long referenceId;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "row_version", insertable = false, updatable = false)
    private Integer rowVersion;

    protected NotificationJpaEntity() {
    }

    public NotificationJpaEntity(Long userId, Short notificationTypeId, String title, String message,
                                 String referenceEntity, Long referenceId, Instant sentAt) {
        this.userId = userId;
        this.notificationTypeId = notificationTypeId;
        this.title = title;
        this.message = message;
        this.referenceEntity = referenceEntity;
        this.referenceId = referenceId;
        this.sentAt = sentAt;
        this.createdAt = sentAt;
    }

    /** Lo único que cambia de una notificación ya enviada: leída o no, y si se borró. */
    public void applyState(Instant readAt, Instant deletedAt) {
        this.readAt = readAt;
        this.read = readAt != null;
        this.deletedAt = deletedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Short getNotificationTypeId() {
        return notificationTypeId;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public String getReferenceEntity() {
        return referenceEntity;
    }

    public Long getReferenceId() {
        return referenceId;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
