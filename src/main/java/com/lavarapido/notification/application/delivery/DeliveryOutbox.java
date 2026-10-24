package com.lavarapido.notification.application.delivery;

import com.lavarapido.notification.domain.model.Notification;

import java.util.List;

/**
 * Por dónde salen las entregas planeadas de una notificación ya guardada (OPS-05). La de producción
 * (PersistentDeliveryOutbox) las guarda en la misma transacción y las envía después del commit, con
 * reintentos; ImmediateDeliveryOutbox las ejecuta enseguida (pruebas y uso sin base).
 */
public interface DeliveryOutbox {

    void submit(Notification saved, List<DeliveryCommand> deliveries);
}
