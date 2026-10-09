package com.lavarapido.notification.infrastructure.adapter.in.web;

import com.lavarapido.notification.application.usecase.EmailRecipients;
import com.lavarapido.notification.domain.exception.NotificationTypeNotAllowedException;
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
import java.util.Set;

/**
 * El administrador envía un mensaje a usuarios concretos (ej. "cerramos temprano hoy", o el aviso
 * de que terminó la inspección de un vehículo con type INSPECTION_REPORT). Además es la forma de
 * probar la bandeja y las push. Solo ADMIN (SecurityConfig).
 *
 * El correo sigue la misma regla que los eventos (EmailRecipients): INSPECTION_REPORT también se
 * envía por correo; un SYSTEM_MESSAGE se queda en la bandeja.
 *
 * Solo acepta esos dos tipos: los demás (pago aprobado, reserva confirmada...) los generan los
 * eventos, y uno escrito a mano llegaría al cliente como si fuera real (400 NOTIFICATION_TYPE_NOT_ALLOWED).
 */
@RestController
@RequestMapping("/api/v1/admin/notifications")
class AdminNotificationController {

    private static final Set<NotificationTypeCode> ADMIN_TYPES =
            Set.of(NotificationTypeCode.SYSTEM_MESSAGE, NotificationTypeCode.INSPECTION_REPORT);

    private final SendNotificationUseCase sender;
    private final EmailRecipients emailRecipients;

    AdminNotificationController(SendNotificationUseCase sender, EmailRecipients emailRecipients) {
        this.sender = sender;
        this.emailRecipients = emailRecipients;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Integer> send(@Valid @RequestBody AdminNotificationRequest request) {
        NotificationTypeCode type = request.type() == null || request.type().isBlank()
                ? NotificationTypeCode.SYSTEM_MESSAGE
                : NotificationTypeCode.of(request.type().strip());
        if (!ADMIN_TYPES.contains(type)) {
            throw new NotificationTypeNotAllowedException(type.name());
        }
        List<SendNotificationCommand> commands = request.userIds().stream().distinct()
                .map(userId -> new SendNotificationCommand(userId, type, request.title(), request.message(), null, null))
                .map(emailRecipients::withEmail)
                .toList();
        return Map.of("sent", sender.sendAll(commands).size());
    }
}
