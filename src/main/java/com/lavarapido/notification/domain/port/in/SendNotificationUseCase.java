package com.lavarapido.notification.domain.port.in;

import com.lavarapido.notification.domain.model.Notification;

import java.util.List;

/** Crea notificaciones y las envía por push a los celulares registrados del destinatario. */
public interface SendNotificationUseCase {

    Notification send(SendNotificationCommand command);

    List<Notification> sendAll(List<SendNotificationCommand> commands);
}
