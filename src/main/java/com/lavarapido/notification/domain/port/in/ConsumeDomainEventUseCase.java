package com.lavarapido.notification.domain.port.in;

import com.lavarapido.notification.domain.model.DomainEventEnvelope;

/** Reacción a los eventos de los demás servicios (booking, operations, payment, security). */
public interface ConsumeDomainEventUseCase {

    void handle(DomainEventEnvelope event);
}
