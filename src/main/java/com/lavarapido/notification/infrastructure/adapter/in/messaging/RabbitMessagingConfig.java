package com.lavarapido.notification.infrastructure.adapter.in.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Topología de RabbitMQ del servicio (cross-cutting.md §7): un exchange topic compartido,
 * carwash.events, y una cola propia enlazada solo a los eventos que generan notificación.
 *
 * Los mensajes que fallan varias veces van a la cola de "cartas muertas" (DLQ) para revisarlos,
 * en vez de perderse o reintentarse para siempre.
 *
 * Solo se activa con app.messaging.enabled=true: mientras los otros servicios no publiquen
 * eventos, este servicio funciona sin RabbitMQ.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.messaging", name = "enabled", havingValue = "true")
class RabbitMessagingConfig {

    static final String EXCHANGE = "carwash.events";
    static final String QUEUE = "notification-service.events";
    static final String DEAD_LETTER_EXCHANGE = "carwash.events.dlx";
    static final String DEAD_LETTER_QUEUE = "notification-service.events.dlq";

    /** Routing keys de cross-cutting.md §7 que generan notificación. */
    static final List<String> ROUTING_KEYS = List.of(
            "security.user_registered",
            "booking.created",
            "booking.confirmed",
            "booking.cancelled",
            "execution.operator_assigned",
            "execution.service_started",
            "execution.service_completed",
            "payment.confirmed",
            "payment.rejected");

    @Bean
    Declarables notificationTopology() {
        TopicExchange exchange = new TopicExchange(EXCHANGE, true, false);
        DirectExchange deadLetterExchange = new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
        Queue deadLetterQueue = QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
        Queue queue = QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(DEAD_LETTER_QUEUE)
                .build();

        List<org.springframework.amqp.core.Declarable> declarables = new ArrayList<>(
                List.of(exchange, deadLetterExchange, deadLetterQueue, queue,
                        BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(DEAD_LETTER_QUEUE)));
        for (String routingKey : ROUTING_KEYS) {
            Binding binding = BindingBuilder.bind(queue).to(exchange).with(routingKey);
            declarables.add(binding);
        }
        return new Declarables(declarables);
    }
}
