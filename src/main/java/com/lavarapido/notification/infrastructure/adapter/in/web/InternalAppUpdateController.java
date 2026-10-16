package com.lavarapido.notification.infrastructure.adapter.in.web;

import com.lavarapido.notification.domain.port.in.AppUpdateNoticeUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ruta interna (llave X-Internal-Key, el gateway no la publica): la llama deploy/publish-apk.sh de
 * lavarapido-infra después de publicar un APK nuevo en el servidor.
 */
@RestController
@RequestMapping("/internal/v1/notifications")
class InternalAppUpdateController {

    private final AppUpdateNoticeUseCase notices;

    InternalAppUpdateController(AppUpdateNoticeUseCase notices) {
        this.notices = notices;
    }

    @PostMapping("/app-update")
    AppUpdateResponse announce(@Valid @RequestBody AppUpdateRequest request) {
        return new AppUpdateResponse(request.versionCode(), notices.announce(request.versionCode()));
    }

    record AppUpdateRequest(@NotNull(message = "versionCode is required")
                            @Positive(message = "versionCode must be positive") Integer versionCode) {
    }

    /** notified: a cuántos usuarios se les avisó (0 si esa versión ya se había anunciado). */
    record AppUpdateResponse(int versionCode, int notified) {
    }
}
