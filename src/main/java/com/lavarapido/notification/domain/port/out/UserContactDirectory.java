package com.lavarapido.notification.domain.port.out;

import java.util.List;
import java.util.Optional;

/**
 * El correo y el nombre de un usuario, consultados a security-service cuando hay que escribirle
 * (ADR-011, sección 8). Este servicio no guarda correos: siempre usa el actual. También dice
 * quiénes son los administradores, para los avisos del personal.
 */
public interface UserContactDirectory {

    /** Vacío si no se pudo consultar o si la cuenta no existe: la notificación sigue sin correo. */
    Optional<UserContact> contactOf(long userId);

    /** Ids de los administradores activos. Vacío si no se pudo consultar: no se avisa a nadie. */
    List<Long> activeAdminIds();

    record UserContact(long userId, String email, String firstName, boolean active) {
    }
}
