package com.lavarapido.notification.infrastructure.adapter.out.push;

import com.lavarapido.notification.domain.model.DeviceToken;
import com.lavarapido.notification.domain.model.Notification;
import com.lavarapido.notification.domain.port.out.DeviceTokenRepository;
import com.lavarapido.notification.domain.port.out.PushSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Envía las push con el servicio de Expo (gratis): Expo las entrega a Firebase (Android) o a
 * Apple (iOS). Corre en otro hilo (@Async): la petición del usuario no espera a Expo, y si Expo
 * falla la notificación igual quedó guardada en la bandeja.
 *
 * Si Expo responde DeviceNotRegistered (desinstalaron la app), ese token se desactiva para no
 * seguir enviándole.
 */
@Component
@ConditionalOnProperty(prefix = "app.push", name = "enabled", havingValue = "true")
class ExpoPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(ExpoPushSender.class);
    // Expo acepta hasta 100 mensajes por petición
    private static final int MAX_BATCH = 100;

    private final RestClient client;
    private final DeviceTokenRepository devices;

    ExpoPushSender(PushProperties properties, DeviceTokenRepository devices) {
        this.devices = devices;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.timeout());
        requestFactory.setReadTimeout(properties.timeout());
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.expoUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        if (properties.hasAccessToken()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.accessToken());
        }
        this.client = builder.build();
    }

    @Async
    @Override
    public void send(Notification notification, List<DeviceToken> targets) {
        for (int start = 0; start < targets.size(); start += MAX_BATCH) {
            sendBatch(notification, targets.subList(start, Math.min(start + MAX_BATCH, targets.size())));
        }
    }

    private void sendBatch(Notification notification, List<DeviceToken> batch) {
        List<ExpoMessage> messages = batch.stream().map(device -> ExpoMessage.of(notification, device)).toList();
        try {
            ExpoResponse response = client.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(messages)
                    .retrieve()
                    .body(ExpoResponse.class);
            handleTickets(notification, batch, response);
        } catch (RestClientException e) {
            // el token del celular nunca va al log: basta con el id de la notificación
            log.warn("Push for notification {} could not be sent to Expo: {}",
                    notification.notificationId(), e.getMessage());
        }
    }

    // la respuesta trae un "ticket" por mensaje, en el mismo orden en que se enviaron
    private void handleTickets(Notification notification, List<DeviceToken> batch, ExpoResponse response) {
        if (response == null || response.data() == null) {
            return;
        }
        int ok = 0;
        for (int i = 0; i < Math.min(batch.size(), response.data().size()); i++) {
            ExpoTicket ticket = response.data().get(i);
            if ("ok".equals(ticket.status())) {
                ok++;
            } else if (ticket.details() != null && "DeviceNotRegistered".equals(ticket.details().get("error"))) {
                devices.deactivate(batch.get(i).token());
                log.info("Device {} no longer has the app installed: token deactivated", batch.get(i).deviceTokenId());
            } else {
                log.warn("Expo rejected the push for notification {}: {}", notification.notificationId(), ticket.message());
            }
        }
        log.info("Push for notification {} accepted by Expo for {}/{} device(s)",
                notification.notificationId(), ok, batch.size());
    }

    /** Mensaje de la API de Expo. data viaja oculto: la app lo usa para abrir la pantalla correcta. */
    record ExpoMessage(String to, String title, String body, String sound, String channelId,
                       Map<String, Object> data) {

        static ExpoMessage of(Notification notification, DeviceToken device) {
            Map<String, Object> data = new HashMap<>();
            data.put("notificationId", notification.notificationId());
            data.put("type", notification.type().name());
            if (notification.referenceEntity() != null) {
                data.put("referenceEntity", notification.referenceEntity());
                data.put("referenceId", notification.referenceId());
            }
            return new ExpoMessage(device.token(), notification.title(), notification.message(), "default",
                    "default", data);
        }
    }

    record ExpoResponse(List<ExpoTicket> data) {
    }

    record ExpoTicket(String status, String id, String message, Map<String, Object> details) {
    }
}
