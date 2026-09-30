package com.lavarapido.notification.infrastructure.adapter.in.web;

import com.lavarapido.notification.domain.model.NotificationCategory;
import com.lavarapido.notification.domain.model.NotificationFilter;
import com.lavarapido.notification.domain.port.in.NotificationInboxUseCase;
import com.lavarapido.notification.infrastructure.adapter.in.web.dto.NotificationResponse;
import com.lavarapido.notification.infrastructure.adapter.in.web.dto.PageResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/**
 * La bandeja de notificaciones de quien llama (RF-018). Ningún método recibe el id del usuario:
 * sale del token, así nadie puede leer ni marcar notificaciones de otro.
 */
@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController {

    private final NotificationInboxUseCase inbox;

    NotificationController(NotificationInboxUseCase inbox) {
        this.inbox = inbox;
    }

    /** Filtros opcionales: read (true/false), category (REMINDER, PROMOTION...), from/to (aaaa-mm-dd). */
    @GetMapping
    PageResponse<NotificationResponse> list(@AuthenticationPrincipal Jwt jwt,
                                            @RequestParam(required = false) Boolean read,
                                            @RequestParam(required = false) NotificationCategory category,
                                            @RequestParam(required = false)
                                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                            @RequestParam(required = false)
                                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        long userId = AuthenticatedUser.from(jwt).userId();
        NotificationFilter filter = new NotificationFilter(read, category, from, to);
        return PageResponse.from(inbox.list(userId, filter, page, size).map(NotificationResponse::from));
    }

    /** El número de la campanita. */
    @GetMapping("/unread-count")
    Map<String, Long> unreadCount(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("count", inbox.countUnread(AuthenticatedUser.from(jwt).userId()));
    }

    @PatchMapping("/{id}/read")
    NotificationResponse markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return NotificationResponse.from(inbox.markRead(AuthenticatedUser.from(jwt).userId(), id));
    }

    @PatchMapping("/{id}/unread")
    NotificationResponse markUnread(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return NotificationResponse.from(inbox.markUnread(AuthenticatedUser.from(jwt).userId(), id));
    }

    @PostMapping("/read-all")
    Map<String, Integer> markAllRead(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("updated", inbox.markAllRead(AuthenticatedUser.from(jwt).userId()));
    }

    /** Borrado lógico (ningún servicio tiene permiso DELETE en la base): responde 204 sin cuerpo. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        inbox.delete(AuthenticatedUser.from(jwt).userId(), id);
    }
}
