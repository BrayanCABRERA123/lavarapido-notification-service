package com.lavarapido.notification.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@DisplayName("Sobre de los eventos de carwash.events")
class DomainEventEnvelopeTest {

    @Test
    @DisplayName("un campo del payload en null se ignora en vez de mandar el mensaje a la DLQ")
    void nullPayloadValuesAreIgnored() throws Exception {
        // así llega el aviso de operations cuando no conoce al cliente (lectura interna de booking)
        String body = "{\"eventId\":\"e-1\",\"eventType\":\"ServiceNotStarted\",\"aggregateId\":\"60048\","
                + "\"occurredAt\":\"2026-10-21T19:23:16.587367200Z\",\"version\":1,\"payload\":{\"bookingId\":60048,"
                + "\"bookingCode\":\"RES-060048\",\"customerUserId\":null,\"operatorUserId\":2}}";
        JsonMapper json = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

        DomainEventEnvelope event = json.readValue(body, DomainEventEnvelope.class);

        assertEquals(2, ((Number) event.payload().get("operatorUserId")).intValue());
        assertFalse(event.payload().containsKey("customerUserId"));
        Map<String, Object> withNull = new HashMap<>();
        withNull.put("bookingId", 1);
        withNull.put("customerUserId", null);
        assertEquals(Map.of("bookingId", 1), new DomainEventEnvelope("e-2", "X", "1", null, 1, withNull).payload());
    }
}
