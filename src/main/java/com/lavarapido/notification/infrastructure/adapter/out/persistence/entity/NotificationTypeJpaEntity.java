package com.lavarapido.notification.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Fila del catálogo notification.notification_type (solo lectura: lo carga el seed 108). */
@Entity
@Table(schema = "notification", name = "notification_type")
public class NotificationTypeJpaEntity {

    @Id
    @Column(name = "notification_type_id")
    private Short id;

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "deleted_at")
    private java.time.Instant deletedAt;

    protected NotificationTypeJpaEntity() {
    }

    public Short getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    /** Se puede usar si está activo y no se borró. */
    public boolean isUsable() {
        return active && deletedAt == null;
    }
}
