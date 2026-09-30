package com.lavarapido.notification.domain.exception;

/**
 * Error de negocio con un code fijo (ej. NOTIFICATION_NOT_FOUND) que el frontend traduce.
 * El mensaje es para los logs, nunca se le muestra tal cual al usuario.
 */
public abstract class DomainException extends RuntimeException {

    private final String code;

    protected DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
