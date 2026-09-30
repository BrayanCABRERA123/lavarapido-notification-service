package com.lavarapido.notification.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Mensaje que el administrador envía a uno o varios usuarios (los user_id salen de la lista de
 * usuarios del security-service). type es opcional: por defecto SYSTEM_MESSAGE.
 */
public record AdminNotificationRequest(
        @NotEmpty(message = "userIds is required")
        @Size(max = 100, message = "at most 100 users per request")
        List<@Positive(message = "user ids must be positive") Long> userIds,
        String type,
        @NotBlank(message = "title is required") @Size(max = 120, message = "title is too long") String title,
        @NotBlank(message = "message is required") @Size(max = 500, message = "message is too long") String message) {
}
