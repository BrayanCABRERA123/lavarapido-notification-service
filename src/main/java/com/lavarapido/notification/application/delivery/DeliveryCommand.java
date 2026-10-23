package com.lavarapido.notification.application.delivery;

/**
 * Patrón Command: una entrega de una notificación ya guardada por un canal externo (push o
 * correo). Cada comando lleva todo lo que necesita para ejecutarse, así quien decide qué canales
 * aplican (DeliveryPlanner) queda separado de quien los ejecuta (DeliveryInvoker).
 *
 * Agregar un canal nuevo (SMS, WhatsApp...) es escribir otro comando y planearlo; el servicio
 * que guarda la notificación no cambia.
 */
public interface DeliveryCommand {

    /** Nombre corto del canal para el log y el reporte ("push", "email"). */
    String channel();

    /** Entrega la notificación por este canal. Puede lanzar: el invocador aísla el fallo. */
    void execute();
}
