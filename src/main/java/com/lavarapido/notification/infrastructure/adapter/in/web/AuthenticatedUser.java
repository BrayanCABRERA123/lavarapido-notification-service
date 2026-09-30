package com.lavarapido.notification.infrastructure.adapter.in.web;

import com.lavarapido.notification.domain.exception.InvalidValueException;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Quién está llamando, leído del token ya verificado. El claim sub es el user_id de
 * security.app_user, que es justo el user_id de notification.notification.
 */
record AuthenticatedUser(long userId) {

    static AuthenticatedUser from(Jwt jwt) {
        String subject = jwt == null ? null : jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new InvalidValueException("INVALID_TOKEN", "The token has no subject claim");
        }
        try {
            return new AuthenticatedUser(Long.parseLong(subject));
        } catch (NumberFormatException e) {
            throw new InvalidValueException("INVALID_TOKEN", "The token subject is not a valid user id");
        }
    }
}
