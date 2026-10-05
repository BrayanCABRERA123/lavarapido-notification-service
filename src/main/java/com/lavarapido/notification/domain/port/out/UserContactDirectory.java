package com.lavarapido.notification.domain.port.out;

import java.util.Optional;

/**
 * El correo y el nombre de un usuario, consultados a security-service cuando hay que escribirle
 * (ADR-011, sección 8). Este servicio no guarda correos: siempre usa el actual.
 */
public interface UserContactDirectory {

    /** Vacío si no se pudo consultar o si la cuenta no existe: la notificación sigue sin correo. */
    Optional<UserContact> contactOf(long userId);

    record UserContact(long userId, String email, String firstName, boolean active) {
    }
}
