package com.lavarapido.notification.infrastructure.adapter.out.email;

import com.lavarapido.notification.domain.model.Notification;
import com.lavarapido.notification.domain.port.out.EmailSender;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;

/**
 * Envía la notificación por correo con SMTP (Gmail, Mailpit...), la misma configuración MAIL_*
 * que usa el security-service. Se activa con app.mail.enabled=true.
 *
 * Asíncrono: la reacción al evento no espera al servidor de correo. Si falla, queda en el log
 * (sin el correo del usuario) y la notificación sigue en la bandeja.
 */
@Component
@ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "true")
class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;
    private final String from;

    SmtpEmailSender(JavaMailSender mailSender, @Value("${app.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Async
    @Override
    public void send(Notification notification, String toAddress, String recipientName) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(toAddress);
            helper.setSubject(notification.title() + " — LavaRápido");
            // texto plano + HTML: los clientes de correo que no muestran HTML igual lo leen
            helper.setText(plainText(notification, recipientName), html(notification, recipientName));
            mailSender.send(message);
            log.info("Email for notification {} ({}) sent", notification.notificationId(), notification.type());
        } catch (MessagingException | MailException exception) {
            log.error("Could not send the email for notification {}: {}",
                    notification.notificationId(), exception.getMessage());
        }
    }

    static String plainText(Notification notification, String recipientName) {
        return """
                %s

                %s

                —
                LavaRápido · Este correo se envió automáticamente, no es necesario responderlo.
                """.formatted(greeting(recipientName), notification.message());
    }

    static String html(Notification notification, String recipientName) {
        return """
                <div style="font-family:Arial,Helvetica,sans-serif;max-width:520px;margin:0 auto;color:#1a1a2e">
                  <div style="background:#2ec4b6;color:#ffffff;padding:20px 24px;border-radius:12px 12px 0 0">
                    <h1 style="margin:0;font-size:20px">%s</h1>
                  </div>
                  <div style="border:1px solid #e6eaea;border-top:0;padding:24px;border-radius:0 0 12px 12px">
                    <p style="margin:0 0 12px">%s</p>
                    <p style="margin:0 0 20px;line-height:1.5">%s</p>
                    <p style="margin:0;font-size:12px;color:#9aa5ab">
                      LavaRápido · Este correo se envió automáticamente, no es necesario responderlo.
                    </p>
                  </div>
                </div>
                """.formatted(HtmlUtils.htmlEscape(notification.title()),
                HtmlUtils.htmlEscape(greeting(recipientName)),
                HtmlUtils.htmlEscape(notification.message()));
    }

    private static String greeting(String recipientName) {
        return recipientName == null || recipientName.isBlank() ? "Hola," : "Hola, " + recipientName.strip() + ":";
    }
}
