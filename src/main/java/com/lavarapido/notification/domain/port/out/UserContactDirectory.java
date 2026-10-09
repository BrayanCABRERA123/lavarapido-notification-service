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

    /**
     * Contacto del usuario y por qué canales quiere sus avisos (Configuración > Notificaciones en
     * security-service). Los canales llegan null si security-service todavía no los manda: se toman
     * como activos para no dejar de avisar por un dato que falta.
     */
    record UserContact(long userId, String email, String firstName, boolean active, Boolean pushEnabled,
                       Boolean emailRemindersEnabled, Boolean promotionsEnabled) {

        public boolean allowsPush() {
            return !Boolean.FALSE.equals(pushEnabled);
        }

        public boolean allowsEmailReminders() {
            return !Boolean.FALSE.equals(emailRemindersEnabled);
        }

        public boolean allowsPromotions() {
            return !Boolean.FALSE.equals(promotionsEnabled);
        }
    }
}
