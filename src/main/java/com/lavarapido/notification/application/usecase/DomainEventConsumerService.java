package com.lavarapido.notification.application.usecase;

import com.lavarapido.notification.domain.model.DomainEventEnvelope;
import com.lavarapido.notification.domain.port.in.ConsumeDomainEventUseCase;
import com.lavarapido.notification.domain.port.in.SendNotificationCommand;
import com.lavarapido.notification.domain.port.in.SendNotificationUseCase;
import com.lavarapido.notification.domain.port.out.ProcessedEventRepository;
import com.lavarapido.notification.domain.service.EventNotificationFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Convierte los eventos de los otros servicios en notificaciones.
 *
 * Idempotente (cross-cutting.md §7): el eventId se registra en la misma transacción que las
 * notificaciones, así un evento repetido no las duplica y uno que falló se puede reintentar.
 */
@Service
public class DomainEventConsumerService implements ConsumeDomainEventUseCase {

    private static final Logger log = LoggerFactory.getLogger(DomainEventConsumerService.class);

    private final ProcessedEventRepository processedEvents;
    private final SendNotificationUseCase sender;
    private final EventNotificationFactory factory;

    public DomainEventConsumerService(ProcessedEventRepository processedEvents, SendNotificationUseCase sender,
                                      EventNotificationFactory factory) {
        this.processedEvents = processedEvents;
        this.sender = sender;
        this.factory = factory;
    }

    @Override
    @Transactional
    public void handle(DomainEventEnvelope event) {
        if (event.eventId() == null || event.eventId().isBlank()) {
            log.warn("Event {} without eventId ignored: it cannot be deduplicated", event.eventType());
            return;
        }
        if (processedEvents.exists(event.eventId())) {
            log.info("Event {} ({}) already processed, ignoring the duplicate", event.eventId(), event.eventType());
            return;
        }

        List<SendNotificationCommand> commands = factory.from(event);
        sender.sendAll(commands);
        processedEvents.record(event.eventId(), event.eventType());
        log.info("Event {} ({}) produced {} notification(s)", event.eventId(), event.eventType(), commands.size());
    }
}
