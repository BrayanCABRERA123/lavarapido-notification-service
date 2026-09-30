package com.lavarapido.notification.infrastructure.adapter.in.messaging;

import com.lavarapido.notification.domain.model.DomainEventEnvelope;
import com.lavarapido.notification.domain.port.in.ConsumeDomainEventUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Adaptador de entrada: recibe los eventos de carwash.events y se los pasa al caso de uso.
 *
 * Un mensaje que no es JSON válido se manda directo a la DLQ (reintentarlo no lo arregla).
 * Cualquier otro error se reintenta (spring.rabbitmq.listener.simple.retry) y, si sigue
 * fallando, también termina en la DLQ.
 */
@Component
@ConditionalOnProperty(prefix = "app.messaging", name = "enabled", havingValue = "true")
class DomainEventListener {

    private static final Logger log = LoggerFactory.getLogger(DomainEventListener.class);

    private final ConsumeDomainEventUseCase consumer;
    private final JsonMapper json = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    DomainEventListener(ConsumeDomainEventUseCase consumer) {
        this.consumer = consumer;
    }

    @RabbitListener(queues = RabbitMessagingConfig.QUEUE)
    void onEvent(Message message) {
        DomainEventEnvelope event;
        try {
            event = json.readValue(message.getBody(), DomainEventEnvelope.class);
        } catch (JacksonException e) {
            log.warn("Message on {} is not a valid event envelope, sent to the DLQ", RabbitMessagingConfig.QUEUE);
            throw new AmqpRejectAndDontRequeueException("Invalid event envelope", e);
        }
        consumer.handle(event);
    }
}
