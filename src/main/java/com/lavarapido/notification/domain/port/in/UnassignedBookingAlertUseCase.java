package com.lavarapido.notification.domain.port.in;

/**
 * Avisa a los administradores de las reservas confirmadas que empiezan pronto (2 horas por defecto)
 * y todavía no tienen operario. Lo llama la tarea programada; cada reserva se avisa una sola vez.
 */
public interface UnassignedBookingAlertUseCase {

    /** @return cuántos avisos se enviaron en esta vuelta */
    int alertDue();
}
