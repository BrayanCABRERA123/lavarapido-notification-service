package com.lavarapido.notification.domain.port.in;

import com.lavarapido.notification.domain.model.LocalizedText;
import com.lavarapido.notification.domain.model.NotificationTypeCode;
import com.lavarapido.notification.domain.service.NotificationTexts;

/**
 * Lo necesario para crear (y enviar por push) una notificación a un usuario.
 *
 * email y recipientName son opcionales: si vienen, la notificación también se envía por correo.
 * Hoy solo los trae la bienvenida (el evento UserRegistered incluye el correo, ADR-011); este
 * servicio no guarda correos de nadie.
 *
 * titleText y messageText son las plantillas del aviso (RF-020): title y message vienen en español
 * y, antes de enviar, se vuelven a armar en el idioma de la cuenta de quien lo recibe. Un aviso
 * escrito a mano por el administrador no trae plantillas y sale tal cual.
 */
public record SendNotificationCommand(long userId, NotificationTypeCode type, String title, String message,
                                      String referenceEntity, Long referenceId, String email,
                                      String recipientName, LocalizedText titleText, LocalizedText messageText) {

    /** Notificación con correo, sin plantillas (texto fijo). */
    public SendNotificationCommand(long userId, NotificationTypeCode type, String title, String message,
                                   String referenceEntity, Long referenceId, String email, String recipientName) {
        this(userId, type, title, message, referenceEntity, referenceId, email, recipientName, null, null);
    }

    /** Notificación sin correo (bandeja + push), sin plantillas. */
    public SendNotificationCommand(long userId, NotificationTypeCode type, String title, String message,
                                   String referenceEntity, Long referenceId) {
        this(userId, type, title, message, referenceEntity, referenceId, null, null);
    }

    /** Aviso con plantillas: el texto en español se arma aquí y se traduce al enviarlo. */
    public static SendNotificationCommand localized(long userId, NotificationTypeCode type, LocalizedText title,
                                                    LocalizedText message, String referenceEntity, Long referenceId) {
        NotificationTexts texts = NotificationTexts.defaults();
        return new SendNotificationCommand(userId, type, texts.render(title, NotificationTexts.DEFAULT_LANGUAGE),
                texts.render(message, NotificationTexts.DEFAULT_LANGUAGE), referenceEntity, referenceId, null, null,
                title, message);
    }

    public boolean hasEmail() {
        return email != null && !email.isBlank();
    }

    public boolean isLocalized() {
        return titleText != null && messageText != null;
    }

    /** El mismo aviso con el correo y el nombre de quien lo recibe. */
    public SendNotificationCommand withEmail(String email, String recipientName) {
        return new SendNotificationCommand(userId, type, title, message, referenceEntity, referenceId, email,
                recipientName, titleText, messageText);
    }

    /** El mismo aviso escrito en el idioma indicado (si no trae plantillas, queda igual). */
    public SendNotificationCommand in(String language, NotificationTexts texts) {
        if (!isLocalized()) {
            return this;
        }
        return new SendNotificationCommand(userId, type, texts.render(titleText, language),
                texts.render(messageText, language), referenceEntity, referenceId, email, recipientName,
                titleText, messageText);
    }
}
