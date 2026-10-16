package com.lavarapido.notification.application.usecase;

import com.lavarapido.notification.domain.exception.InvalidValueException;
import com.lavarapido.notification.domain.model.LocalizedText;
import com.lavarapido.notification.domain.model.NotificationTypeCode;
import com.lavarapido.notification.domain.port.in.AppUpdateNoticeUseCase;
import com.lavarapido.notification.domain.port.in.SendNotificationCommand;
import com.lavarapido.notification.domain.port.in.SendNotificationUseCase;
import com.lavarapido.notification.domain.port.out.DeviceTokenRepository;
import com.lavarapido.notification.domain.port.out.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Aviso de versión nueva de la app. Solo a quien tiene la app instalada (un celular activo): a
 * quien no la tiene no le sirve. Pasa por el envío normal, así respeta el interruptor de push de
 * cada usuario y el aviso queda también en la bandeja, en el idioma de su cuenta.
 *
 * Cada versión se anuncia una vez (se marca en processed_event): publicar el mismo APK otra vez no
 * repite el aviso. Si un envío falla, los demás usuarios igual reciben el suyo.
 */
@Service
public class AppUpdateNoticeService implements AppUpdateNoticeUseCase {

    private static final Logger log = LoggerFactory.getLogger(AppUpdateNoticeService.class);
    static final String REFERENCE = "app_version";

    private final DeviceTokenRepository devices;
    private final ProcessedEventRepository processed;
    private final SendNotificationUseCase sender;

    public AppUpdateNoticeService(DeviceTokenRepository devices, ProcessedEventRepository processed,
                                  SendNotificationUseCase sender) {
        this.devices = devices;
        this.processed = processed;
        this.sender = sender;
    }

    @Override
    public int announce(int versionCode) {
        if (versionCode <= 0) {
            throw new InvalidValueException("INVALID_VERSION", "The version code must be a positive number");
        }
        String eventId = "app-update-v" + versionCode;
        if (processed.exists(eventId)) {
            log.info("App version {} was already announced", versionCode);
            return 0;
        }
        List<Long> users = devices.usersWithActiveDevices();
        int sent = 0;
        for (Long user : users) {
            try {
                sender.send(SendNotificationCommand.localized(user, NotificationTypeCode.APP_UPDATE_AVAILABLE,
                        LocalizedText.of("appUpdate.title"),
                        LocalizedText.of("appUpdate.message").with("version", versionCode),
                        REFERENCE, (long) versionCode));
                sent++;
            } catch (RuntimeException e) {
                log.warn("The app update notice could not be sent to user {}: {}", user, e.getMessage());
            }
        }
        processed.record(eventId, NotificationTypeCode.APP_UPDATE_AVAILABLE.name());
        log.info("App version {} announced to {}/{} user(s) with the app installed", versionCode, sent, users.size());
        return sent;
    }
}
