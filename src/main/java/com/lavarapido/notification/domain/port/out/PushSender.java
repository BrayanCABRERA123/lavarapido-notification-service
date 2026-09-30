package com.lavarapido.notification.domain.port.out;

import com.lavarapido.notification.domain.model.DeviceToken;
import com.lavarapido.notification.domain.model.Notification;

import java.util.List;

/**
 * Envía una notificación como push a los celulares indicados. La implementación real usa el
 * servicio de push de Expo; si falla, la notificación igual queda en la bandeja.
 */
public interface PushSender {

    void send(Notification notification, List<DeviceToken> devices);
}
