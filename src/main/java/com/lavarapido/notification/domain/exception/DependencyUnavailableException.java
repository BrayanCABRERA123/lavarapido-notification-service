package com.lavarapido.notification.domain.exception;

/**
 * Otro servicio no respondió y sin su respuesta no se puede seguir (OPS-06): por ejemplo, security
 * no dijo quiénes son los administradores. En un evento hace que RabbitMQ lo reintente (no se marca
 * procesado); en una petición sale como 503.
 */
public class DependencyUnavailableException extends DomainException {

    public DependencyUnavailableException(String code, String message) {
        super(code, message);
    }
}
