package com.lavarapido.notification.infrastructure.adapter.in.web;

import com.lavarapido.notification.domain.port.in.DeviceRegistrationUseCase;
import com.lavarapido.notification.infrastructure.adapter.in.web.dto.DeviceRequests.DeviceResponse;
import com.lavarapido.notification.infrastructure.adapter.in.web.dto.DeviceRequests.RegisterDeviceRequest;
import com.lavarapido.notification.infrastructure.adapter.in.web.dto.DeviceRequests.UnregisterDeviceRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Celulares que reciben push. La app los registra al iniciar sesión y los quita al cerrarla. */
@RestController
@RequestMapping("/api/v1/notifications/devices")
class DeviceController {

    private final DeviceRegistrationUseCase devices;

    DeviceController(DeviceRegistrationUseCase devices) {
        this.devices = devices;
    }

    /** PUT porque es idempotente: registrar el mismo token otra vez no crea otra fila. */
    @PutMapping
    DeviceResponse register(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RegisterDeviceRequest request) {
        long userId = AuthenticatedUser.from(jwt).userId();
        return DeviceResponse.from(devices.register(userId, request.token(), request.platform()));
    }

    /** POST y no DELETE con cuerpo: el token lleva corchetes y no conviene ponerlo en la URL. */
    @PostMapping("/unregister")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unregister(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UnregisterDeviceRequest request) {
        devices.unregister(AuthenticatedUser.from(jwt).userId(), request.token());
    }
}
