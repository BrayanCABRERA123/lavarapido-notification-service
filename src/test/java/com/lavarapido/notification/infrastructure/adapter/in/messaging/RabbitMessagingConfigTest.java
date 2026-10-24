package com.lavarapido.notification.infrastructure.adapter.in.messaging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Topología de RabbitMQ de notification (cola, DLQ y enlaces)")
class RabbitMessagingConfigTest {

    private final Declarables topology = new RabbitMessagingConfig().notificationTopology();

    private List<String> routingKeysOf(String queue) {
        return topology.getDeclarablesByType(Binding.class).stream()
                .filter(binding -> binding.getDestination().equals(queue))
                .map(Binding::getRoutingKey).toList();
    }

    @Test
    @DisplayName("OPS-09: lo que sigue fallando después de los reintentos va a la DLQ")
    void theQueueHasADeadLetterQueue() {
        Queue queue = topology.getDeclarablesByType(Queue.class).stream()
                .filter(q -> q.getName().equals(RabbitMessagingConfig.QUEUE)).findFirst().orElseThrow();

        assertEquals(RabbitMessagingConfig.DEAD_LETTER_EXCHANGE, queue.getArguments().get("x-dead-letter-exchange"));
        assertEquals(RabbitMessagingConfig.DEAD_LETTER_QUEUE, queue.getArguments().get("x-dead-letter-routing-key"));
        assertEquals(List.of(RabbitMessagingConfig.DEAD_LETTER_QUEUE), routingKeysOf(RabbitMessagingConfig.DEAD_LETTER_QUEUE));
    }

    @Test
    @DisplayName("OPS-10: no se enlaza booking.created (nadie lo publica); sí los eventos que generan avisos")
    void onlyEventsThatSomebodyPublishes() {
        List<String> keys = routingKeysOf(RabbitMessagingConfig.QUEUE);

        assertFalse(keys.contains("booking.created"));
        assertTrue(keys.containsAll(List.of("booking.confirmed", "booking.status_changed", "execution.service_not_started",
                "execution.inspection_shared", "payment.confirmed")));
    }
}
