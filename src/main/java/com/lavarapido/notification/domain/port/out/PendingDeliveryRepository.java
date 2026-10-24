package com.lavarapido.notification.domain.port.out;

import com.lavarapido.notification.domain.model.PendingDelivery;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Entregas de push y correo pendientes o por reintentar (OPS-05, notification.pending_delivery). */
public interface PendingDeliveryRepository {

    /** Guarda las entregas nuevas (listas para salir ya) y devuelve sus ids. */
    List<Long> addAll(List<PendingDelivery> deliveries, Instant now);

    /** Ids de las que ya toca intentar (sin entregar ni abandonar), las más viejas primero. */
    List<Long> findDue(Instant now, int limit);

    /**
     * La toma para enviarla: suma un intento y la aparta hasta leaseUntil, en una sola sentencia. Si otro
     * hilo ya la tomó (o ya salió), vacío: así una entrega nunca sale dos veces a la vez.
     */
    Optional<PendingDelivery> claim(long id, Instant now, Instant leaseUntil);

    void markDelivered(long id, Instant at);

    void markRetry(long id, Instant nextAttemptAt, String error);

    /** Se abandona (tope de intentos o la notificación ya no existe). */
    void markFailed(long id, Instant at, String error);
}
