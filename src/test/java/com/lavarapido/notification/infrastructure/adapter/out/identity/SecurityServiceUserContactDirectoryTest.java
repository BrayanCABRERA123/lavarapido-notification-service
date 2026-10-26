package com.lavarapido.notification.infrastructure.adapter.out.identity;

import com.lavarapido.notification.domain.exception.DependencyUnavailableException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SecurityServiceUserContactDirectory (contactos y administradores en security-service)")
class SecurityServiceUserContactDirectoryTest {

    private HttpServer server;
    private String base;
    // lo que responde el security falso a /internal/v1/users/admins
    private volatile int adminsStatus = 200;
    private volatile String adminsBody = "[1,2]";

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/users/", exchange -> {
            boolean admins = exchange.getRequestURI().getPath().endsWith("/admins");
            int status = admins ? adminsStatus : 500;
            byte[] body = (admins ? adminsBody : "{}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private SecurityServiceUserContactDirectory directory(String key) {
        return new SecurityServiceUserContactDirectory(base, Duration.ofSeconds(2), key);
    }

    @Test
    @DisplayName("OPS-06: con security caído, pedir los administradores lanza (el evento se reintenta)")
    void adminsLookupFailsLoudly() {
        adminsStatus = 503;

        DependencyUnavailableException error = assertThrows(DependencyUnavailableException.class,
                () -> directory("llave").activeAdminIds());
        assertEquals("SECURITY_SERVICE_UNAVAILABLE", error.code());
    }

    @Test
    @DisplayName("OPS-06: una lista vacía no es un error: no hay administradores activos")
    void anEmptyListIsAnAnswer() {
        assertEquals(List.of(1L, 2L), directory("llave").activeAdminIds());
        adminsBody = "[]";
        assertTrue(directory("llave").activeAdminIds().isEmpty());
        // sin llave interna no se llama a nadie (no se arregla reintentando)
        assertTrue(directory("").activeAdminIds().isEmpty());
    }

    @Test
    @DisplayName("el contacto de un usuario sigue siendo tolerante: sin respuesta, la notificación va sin correo")
    void contactLookupStaysTolerant() {
        assertTrue(directory("llave").contactOf(7L).isEmpty());
    }
}
