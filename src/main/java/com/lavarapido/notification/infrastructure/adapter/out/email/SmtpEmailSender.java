package com.lavarapido.notification.infrastructure.adapter.out.email;

import com.lavarapido.notification.domain.model.Notification;
import com.lavarapido.notification.domain.model.LocalizedText;
import com.lavarapido.notification.domain.port.out.EmailSender;
import com.lavarapido.notification.domain.service.NotificationTexts;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.MailPreparationException;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;

/**
 * Envía la notificación por correo con SMTP (Gmail, Mailpit...), la misma configuración MAIL_*
 * que usa el security-service. Se activa con app.mail.enabled=true.
 *
 * OPS-05: corre en el hilo de PendingDeliveryDispatcher, después del commit (la reacción al evento no
 * espera al servidor de correo). Si falla, queda en el log (sin el correo del usuario), lanza el error
 * y la entrega se reintenta; la notificación sigue en la bandeja.
 */
@Component
// @Primary solo desambigua para el analisis estatico: la pareja con @ConditionalOnProperty opuesta nunca se carga a la vez
@Primary
@ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "true")
class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);
    private static final NotificationTexts TEXTS = NotificationTexts.defaults();

    private final JavaMailSender mailSender;
    private final String from;

    SmtpEmailSender(JavaMailSender mailSender, @Value("${app.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(Notification notification, String toAddress, String recipientName) {
        send(notification, toAddress, recipientName, NotificationTexts.DEFAULT_LANGUAGE);
    }

    /** El título y el mensaje ya vienen en el idioma de la cuenta; aquí se escriben el saludo y el pie. */
    @Override
    public void send(Notification notification, String toAddress, String recipientName, String language) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(toAddress);
            helper.setSubject(TEXTS.render(LocalizedText.of("email.subject").with("title", notification.title()), language));
            // texto plano + HTML: los clientes de correo que no muestran HTML igual lo leen
            helper.setText(plainText(notification, recipientName, language), html(notification, recipientName, language));
            mailSender.send(message);
            log.info("Email for notification {} ({}) sent", notification.notificationId(), notification.type());
        } catch (MessagingException | MailException exception) {
            log.error("Could not send the email for notification {}: {}",
                    notification.notificationId(), exception.getMessage());
            // OPS-05: se reintenta más tarde (PendingDeliveryDispatcher)
            throw exception instanceof MailException mail ? mail : new MailPreparationException(exception);
        }
    }

    static String plainText(Notification notification, String recipientName, String language) {
        return """
                %s

                %s

                —
                %s
                """.formatted(greeting(recipientName, language), notification.message(),
                TEXTS.render(LocalizedText.of("email.footer"), language));
    }

    static String html(Notification notification, String recipientName, String language) {
        return """
                <div style="font-family:Arial,Helvetica,sans-serif;max-width:520px;margin:0 auto;color:#1a1a2e">
                  <div style="background:#2ec4b6;color:#ffffff;padding:20px 24px;border-radius:12px 12px 0 0">
                    <h1 style="margin:0;font-size:20px">%s</h1>
                  </div>
                  <div style="border:1px solid #e6eaea;border-top:0;padding:24px;border-radius:0 0 12px 12px">
                    <p style="margin:0 0 12px">%s</p>
                    <p style="margin:0 0 20px;line-height:1.5">%s</p>
                    <p style="margin:0;font-size:12px;color:#9aa5ab">
                      %s
                    </p>
                  </div>
                </div>
                """.formatted(HtmlUtils.htmlEscape(notification.title()),
                HtmlUtils.htmlEscape(greeting(recipientName, language)),
                HtmlUtils.htmlEscape(notification.message()),
                HtmlUtils.htmlEscape(TEXTS.render(LocalizedText.of("email.footer"), language)));
    }

    private static String greeting(String recipientName, String language) {
        return recipientName == null || recipientName.isBlank()
                ? TEXTS.render(LocalizedText.of("email.greeting"), language)
                : TEXTS.render(LocalizedText.of("email.greetingName").with("name", recipientName.strip()), language);
    }
}
