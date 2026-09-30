package com.lavarapido.notification.infrastructure.adapter.out.push;

import com.lavarapido.notification.domain.model.DeviceToken;
import com.lavarapido.notification.domain.model.Notification;
import com.lavarapido.notification.domain.port.out.PushSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Push apagadas (app.push.enabled=false, el valor por defecto): solo se deja constancia en el
 * log. Así el servicio arranca en cualquier PC sin cuenta de Expo; las notificaciones siguen
 * guardándose y se ven en la campanita.
 */
@Component
@ConditionalOnProperty(prefix = "app.push", name = "enabled", havingValue = "false", matchIfMissing = true)
class LoggingPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingPushSender.class);

    @Override
    public void send(Notification notification, List<DeviceToken> devices) {
        log.info("[push disabled] Notification {} would be sent to {} device(s)",
                notification.notificationId(), devices.size());
    }
}
