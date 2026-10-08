package com.lavarapido.notification.domain.model;

import com.lavarapido.notification.domain.exception.UnknownNotificationTypeException;

import java.util.Arrays;

/**
 * Los tipos del catálogo notification_type (seed 108). El code es el mismo texto que la fila de
 * la base, así el frontend puede traducir por tipo aunque el título se guarde ya escrito.
 */
public enum NotificationTypeCode {
    USER_WELCOME(NotificationCategory.SYSTEM),
    BOOKING_CREATED(NotificationCategory.CONFIRMATION),
    BOOKING_CONFIRMED(NotificationCategory.CONFIRMATION),
    BOOKING_CANCELLED(NotificationCategory.CANCELLATION),
    BOOKING_REMINDER(NotificationCategory.REMINDER),
    OPERATOR_ASSIGNED(NotificationCategory.MESSAGE),
    SERVICE_STARTED(NotificationCategory.MESSAGE),
    SERVICE_COMPLETED(NotificationCategory.CONFIRMATION),
    PAYMENT_CONFIRMED(NotificationCategory.CONFIRMATION),
    PAYMENT_REJECTED(NotificationCategory.CANCELLATION),
    PROMOTION_AVAILABLE(NotificationCategory.PROMOTION),
    // confirmación de canje de un cupón en el pago (payment.promotion_redeemed, migración 025)
    PROMOTION_REDEEMED(NotificationCategory.CONFIRMATION),
    // mensaje que escribe el administrador (y la forma de probar el servicio sin los demás)
    SYSTEM_MESSAGE(NotificationCategory.SYSTEM);

    private final NotificationCategory category;

    NotificationTypeCode(NotificationCategory category) {
        this.category = category;
    }

    public NotificationCategory category() {
        return category;
    }

    public static NotificationTypeCode of(String code) {
        return Arrays.stream(values())
                .filter(type -> type.name().equals(code))
                .findFirst()
                .orElseThrow(() -> new UnknownNotificationTypeException(code));
    }
}
