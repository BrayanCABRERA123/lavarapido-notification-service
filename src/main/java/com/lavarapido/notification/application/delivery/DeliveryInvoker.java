package com.lavarapido.notification.application.delivery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Invocador del patrón Command: ejecuta las entregas planeadas una por una y aísla los fallos.
 * Si el correo falla, la push igual sale (y al revés), y la notificación ya guardada en la
 * bandeja no se deshace: los canales externos son extras (ADR-011).
 */
public final class DeliveryInvoker {

    private static final Logger log = LoggerFactory.getLogger(DeliveryInvoker.class);

    /** Ejecuta todos los comandos y devuelve qué canales salieron y cuáles fallaron. */
    public DeliveryReport run(long notificationId, List<DeliveryCommand> commands) {
        List<String> delivered = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (DeliveryCommand command : commands) {
            try {
                command.execute();
                delivered.add(command.channel());
            } catch (RuntimeException e) {
                failed.add(command.channel());
                log.warn("Delivery by {} of notification {} failed: {}", command.channel(), notificationId,
                        e.getMessage());
            }
        }
        return new DeliveryReport(delivered, failed);
    }

    /** Canales por los que salió (o se encoló) la notificación y los que fallaron. */
    public record DeliveryReport(List<String> delivered, List<String> failed) {

        public DeliveryReport {
            delivered = List.copyOf(delivered);
            failed = List.copyOf(failed);
        }

        public boolean deliveredBy(String channel) {
            return delivered.contains(channel);
        }
    }
}
