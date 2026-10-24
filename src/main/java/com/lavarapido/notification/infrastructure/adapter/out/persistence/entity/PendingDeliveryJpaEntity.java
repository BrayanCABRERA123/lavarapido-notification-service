package com.lavarapido.notification.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Fila de notification.pending_delivery (OPS-05, migración 047): una entrega por push o correo. */
@Entity
@Table(schema = "notification", name = "pending_delivery")
public class PendingDeliveryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pending_delivery_id")
    private Long id;

    @Column(name = "notification_id", nullable = false, updatable = false)
    private Long notificationId;

    @Column(name = "channel", nullable = false, updatable = false, length = 10)
    private String channel;

    @Column(name = "email_address", length = 254)
    private String emailAddress;

    @Column(name = "recipient_name", length = 100)
    private String recipientName;

    @Column(name = "language", length = 5)
    private String language;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = 300)
    private String lastError;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PendingDeliveryJpaEntity() {
    }

    public PendingDeliveryJpaEntity(Long notificationId, String channel, String emailAddress, String recipientName,
                                    String language, Instant now) {
        this.notificationId = notificationId;
        this.channel = channel;
        this.emailAddress = emailAddress;
        this.recipientName = recipientName;
        this.language = language;
        this.attempts = 0;
        this.nextAttemptAt = now;
        this.createdAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getNotificationId() {
        return notificationId;
    }

    public String getChannel() {
        return channel;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public String getLanguage() {
        return language;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public Instant getFailedAt() {
        return failedAt;
    }
}
