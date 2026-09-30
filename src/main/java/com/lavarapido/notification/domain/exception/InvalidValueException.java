package com.lavarapido.notification.domain.exception;

/** Un dato que llega mal formado (título vacío, token de dispositivo inválido...). */
public class InvalidValueException extends DomainException {

    public InvalidValueException(String code, String message) {
        super(code, message);
    }
}
