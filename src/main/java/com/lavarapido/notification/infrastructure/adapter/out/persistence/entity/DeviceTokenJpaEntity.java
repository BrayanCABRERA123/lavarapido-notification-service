package com.lavarapido.notification.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Fila de notification.device_token (tabla nueva, ADR-011). */
@Entity
@Table(schema = "notification", name = "device_token")
public class DeviceTokenJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "device_token_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token", nullable = false, length = 200, updatable = false)
    private String token;

    @Column(name = "platform", nullable = false, length = 10)
    private String platform;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "row_version", insertable = false, updatable = false)
    private Integer rowVersion;

    protected DeviceTokenJpaEntity() {
    }

    public DeviceTokenJpaEntity(String token, Instant createdAt) {
        this.token = token;
        this.createdAt = createdAt;
    }

    public void apply(long userId, String platform, boolean active, Instant lastSeenAt) {
        this.userId = userId;
        this.platform = platform;
        this.active = active;
        this.lastSeenAt = lastSeenAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getToken() {
        return token;
    }

    public String getPlatform() {
        return platform;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }
}
