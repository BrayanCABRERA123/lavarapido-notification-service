package com.lavarapido.notification.infrastructure.adapter.out.email;

import com.lavarapido.notification.domain.model.Notification;
import com.lavarapido.notification.domain.port.out.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Correo apagado (app.mail.enabled=false): solo se deja constancia en el log, sin el correo del
 * usuario. La notificación igual queda en la bandeja.
 */
@Component
@ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "false", matchIfMissing = true)
class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(Notification notification, String toAddress, String recipientName) {
        log.info("[mail disabled] Notification {} ({}) would be emailed", notification.notificationId(),
                notification.type());
    }
}
