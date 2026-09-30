package com.lavarapido.notification.infrastructure.adapter.in.web;

import com.lavarapido.notification.domain.model.NotificationTypeCode;
import com.lavarapido.notification.domain.port.in.SendNotificationCommand;
import com.lavarapido.notification.domain.port.in.SendNotificationUseCase;
import com.lavarapido.notification.infrastructure.adapter.in.web.dto.AdminNotificationRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * El administrador envía un mensaje a usuarios concretos (ej. "cerramos temprano hoy").
 * Además es la forma de probar la bandeja y las push mientras booking y payment no publiquen
 * eventos. Solo ADMIN (SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/admin/notifications")
class AdminNotificationController {

    private final SendNotificationUseCase sender;

    AdminNotificationController(SendNotificationUseCase sender) {
        this.sender = sender;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Integer> send(@Valid @RequestBody AdminNotificationRequest request) {
        NotificationTypeCode type = request.type() == null || request.type().isBlank()
                ? NotificationTypeCode.SYSTEM_MESSAGE
                : NotificationTypeCode.of(request.type().strip());
        List<SendNotificationCommand> commands = request.userIds().stream().distinct()
                .map(userId -> new SendNotificationCommand(userId, type, request.title(), request.message(), null, null))
                .toList();
        return Map.of("sent", sender.sendAll(commands).size());
    }
}
