package com.lavarapido.notification.infrastructure.adapter.out.push;

import com.lavarapido.notification.application.fake.Fakes.InMemoryDevices;
import com.lavarapido.notification.domain.model.DevicePlatform;
import com.lavarapido.notification.domain.model.DeviceToken;
import com.lavarapido.notification.domain.model.Notification;
import com.lavarapido.notification.domain.model.NotificationTypeCode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("ExpoPushSender (push con el servicio de Expo)")
class ExpoPushSenderTest {

    private static final String TOKEN = "ExponentPushToken[aaaaaaaaaaaaaaaaaaaa]";
    private static final Instant NOW = Instant.parse("2026-10-23T15:00:00Z");

    private HttpServer server;
    private ExpoPushSender sender;
    private final InMemoryDevices devices = new InMemoryDevices();
    // lo que responde el Expo falso
    private volatile int status = 200;
    private volatile String body = "{\"data\":[{\"status\":\"ok\",\"id\":\"t-1\"}]}";

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/push", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/push";
        sender = new ExpoPushSender(new PushProperties(true, url, null, Duration.ofSeconds(2)), devices);
        devices.save(DeviceToken.register(7L, TOKEN, DevicePlatform.ANDROID, NOW));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private Notification notification() {
        return Notification.restore(1L, 7L, NotificationTypeCode.BOOKING_CONFIRMED, "Reserva confirmada",
                "Tu reserva quedó confirmada", "booking", 50L, NOW, null, null);
    }

    @Test
    @DisplayName("OPS-05: si Expo no responde bien, el error sale para que la entrega se reintente")
    void anExpoFailureIsRethrown() {
        status = 503;
        body = "{}";

        assertThrows(RestClientException.class, () -> sender.send(notification(), devices.findActiveByUser(7L)));
    }

    @Test
    @DisplayName("Expo acepta la push; con DeviceNotRegistered el celular se desactiva y no se reintenta")
    void ticketsAreHandled() {
        assertDoesNotThrow(() -> sender.send(notification(), devices.findActiveByUser(7L)));

        body = "{\"data\":[{\"status\":\"error\",\"message\":\"not registered\",\"details\":{\"error\":\"DeviceNotRegistered\"}}]}";
        assertDoesNotThrow(() -> sender.send(notification(), devices.findActiveByUser(7L)));
        assertFalse(devices.findByToken(TOKEN).orElseThrow().active());
    }
}
