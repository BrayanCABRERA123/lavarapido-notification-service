package com.lavarapido.notification.application.usecase;

import com.lavarapido.notification.application.fake.Fakes.InMemoryContacts;
import com.lavarapido.notification.application.fake.Fakes.InMemoryDevices;
import com.lavarapido.notification.application.fake.Fakes.InMemoryNotifications;
import com.lavarapido.notification.application.fake.Fakes.InMemoryProcessedEvents;
import com.lavarapido.notification.application.fake.Fakes.RecordingEmailSender;
import com.lavarapido.notification.application.fake.Fakes.RecordingPushSender;
import com.lavarapido.notification.domain.exception.InvalidValueException;
import com.lavarapido.notification.domain.model.DevicePlatform;
import com.lavarapido.notification.domain.model.DeviceToken;
import com.lavarapido.notification.domain.model.Notification;
import com.lavarapido.notification.domain.model.NotificationTypeCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Aviso de versión nueva de la app")
class AppUpdateNoticeTest {

    private static final long ANA = 7;
    private static final long LUIS = 8;
    private static final long SIN_APP = 9;

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-16T15:00:00Z"), ZoneOffset.UTC);

    private InMemoryNotifications notifications;
    private InMemoryDevices devices;
    private RecordingPushSender push;
    private InMemoryContacts contacts;
    private AppUpdateNoticeService notices;

    @BeforeEach
    void setUp() {
        notifications = new InMemoryNotifications();
        devices = new InMemoryDevices();
        push = new RecordingPushSender();
        contacts = new InMemoryContacts();
        NotificationSendingService sender = new NotificationSendingService(notifications, devices, push,
                new RecordingEmailSender(), contacts, clock);
        notices = new AppUpdateNoticeService(devices, new InMemoryProcessedEvents(), sender);
        phone(ANA, "ExponentPushToken[anaaaaaaaaaaaaaaaaaa]");
        phone(ANA, "ExponentPushToken[anabbbbbbbbbbbbbbbbb]");
        phone(LUIS, "ExponentPushToken[luisaaaaaaaaaaaaaaaa]");
    }

    private void phone(long user, String token) {
        devices.save(DeviceToken.register(user, token, DevicePlatform.ANDROID, clock.instant()));
    }

    @Test
    @DisplayName("se avisa una vez a cada usuario con la app instalada, por bandeja y push")
    void announcesOncePerUserWithTheApp() {
        int notified = notices.announce(3);

        assertEquals(2, notified);
        assertEquals(2, push.sent.size());
        assertEquals(3, push.devices); // Ana tiene dos celulares
        Notification first = push.sent.get(0);
        assertEquals(NotificationTypeCode.APP_UPDATE_AVAILABLE, first.type());
        assertEquals("app_version", first.referenceEntity());
        assertEquals(3L, first.referenceId());
        assertTrue(first.message().contains("3"));
    }

    @Test
    @DisplayName("quien no tiene la app no recibe nada")
    void usersWithoutTheAppGetNothing() {
        notices.announce(3);

        assertTrue(push.sent.stream().noneMatch(n -> n.userId() == SIN_APP));
    }

    @Test
    @DisplayName("publicar la misma versión otra vez no repite el aviso")
    void theSameVersionIsAnnouncedOnlyOnce() {
        notices.announce(3);

        int again = notices.announce(3);

        assertEquals(0, again);
        assertEquals(2, push.sent.size());
        assertEquals(2, notices.announce(4));
    }

    @Test
    @DisplayName("si el usuario apagó las push, el aviso queda solo en la bandeja")
    void respectsThePushSwitch() {
        contacts.withChannels(LUIS, false, true, true);

        int notified = notices.announce(5);

        assertEquals(2, notified);
        assertEquals(1, push.sent.size());
        assertEquals(ANA, push.sent.get(0).userId());
    }

    @Test
    @DisplayName("el aviso sale en el idioma de la cuenta")
    void isWrittenInTheAccountLanguage() {
        contacts.withLanguage(LUIS, "en");

        notices.announce(6);

        Notification luis = push.sent.stream().filter(n -> n.userId() == LUIS).findFirst().orElseThrow();
        assertTrue(luis.title().contains("new version"), luis.title());
    }

    @Test
    @DisplayName("una versión inválida se rechaza")
    void rejectsAnInvalidVersion() {
        assertThrows(InvalidValueException.class, () -> notices.announce(0));
    }
}
