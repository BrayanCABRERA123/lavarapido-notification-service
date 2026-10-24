package com.lavarapido.notification.domain.model;

/**
 * Una entrega por un canal externo (push o correo) que todavía no sale (OPS-05, tabla
 * notification.pending_delivery, migración 047). Se guarda en la misma transacción que la
 * notificación: si esa transacción se deshace (el evento se reintenta), no queda nada que enviar y no
 * se duplica. Sale después del commit y, si falla, se reintenta hasta un tope de intentos.
 *
 * Para la push no se guardan los celulares: se leen al enviar (pudieron darse de baja). Para el correo
 * se guarda la dirección y el nombre del saludo, que se borran apenas se entrega o se abandona.
 */
public record PendingDelivery(Long id, long notificationId, Channel channel, String emailAddress,
                              String recipientName, String language, int attempts) {

    public enum Channel { PUSH, EMAIL }

    public static PendingDelivery push(long notificationId) {
        return new PendingDelivery(null, notificationId, Channel.PUSH, null, null, null, 0);
    }

    public static PendingDelivery email(long notificationId, String address, String recipientName, String language) {
        return new PendingDelivery(null, notificationId, Channel.EMAIL, address, recipientName, language, 0);
    }
}
