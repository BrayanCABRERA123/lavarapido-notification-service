package com.lavarapido.notification.domain.exception;

/** El code del tipo no está en el catálogo notification_type (seed 108) o está inactivo. */
public class UnknownNotificationTypeException extends DomainException {

    public UnknownNotificationTypeException(String typeCode) {
        super("UNKNOWN_NOTIFICATION_TYPE", "Notification type " + typeCode + " does not exist or is inactive");
    }
}
