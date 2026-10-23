package com.lavarapido.notification.application.usecase;

import com.lavarapido.notification.application.fake.Fakes;
import com.lavarapido.notification.application.fake.Fakes.InMemoryDevices;
import com.lavarapido.notification.application.fake.Fakes.InMemoryNotifications;
import com.lavarapido.notification.application.fake.Fakes.RecordingEmailSender;
import com.lavarapido.notification.application.fake.Fakes.RecordingPushSender;
import com.lavarapido.notification.domain.model.DomainEventEnvelope;
import com.lavarapido.notification.domain.model.LocalizedText;
import com.lavarapido.notification.domain.model.Notification;
import com.lavarapido.notification.domain.model.NotificationTypeCode;
import com.lavarapido.notification.domain.port.in.SendNotificationCommand;
import com.lavarapido.notification.domain.service.EventNotificationFactory;
import com.lavarapido.notification.domain.service.NotificationTexts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Avisos en el idioma de la cuenta (RF-020)")
class LocalizedNotificationsTest {

    private static final long ANA = 7;
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-30T15:00:00Z"), ZoneOffset.UTC);
    private final EventNotificationFactory factory = new EventNotificationFactory();

    private Fakes.InMemoryContacts contacts;
    private NotificationSendingService sender;

    @BeforeEach
    void setUp() {
        contacts = new Fakes.InMemoryContacts();
        sender = new NotificationSendingService(new InMemoryNotifications(), new InMemoryDevices(),
                new RecordingPushSender(), new RecordingEmailSender(), contacts, clock);
    }

    private SendNotificationCommand confirmed() {
        return factory.from(new DomainEventEnvelope("evt-1", "BookingConfirmed", "145", clock.instant(), 1, Map.of(
                "bookingId", 145, "bookingCode", "RES-8910", "customerUserId", ANA,
                "scheduledStart", "2026-10-01T15:00:00Z"))).getFirst();
    }

    @Test
    @DisplayName("una cuenta en inglés recibe el aviso en inglés, con la fecha en su formato")
    void english() {
        contacts.withLanguage(ANA, "en");
        Notification saved = sender.send(confirmed());
        assertEquals("Booking confirmed", saved.title());
        assertEquals("Your booking RES-8910 is confirmed for 10/01/2026 at 10:00 AM. See you at the shop.", saved.message());
    }

    @Test
    @DisplayName("francés y portugués; sin idioma o si security-service no responde, español")
    void otherLanguages() {
        contacts.withLanguage(ANA, "fr");
        assertEquals("Réservation confirmée", sender.send(confirmed()).title());
        contacts.withLanguage(ANA, "pt");
        assertTrue(sender.send(confirmed()).message().contains("01/10/2026 às 10:00"));
        contacts.withLanguage(ANA, null);
        assertEquals("Reserva confirmada", sender.send(confirmed()).title());
        contacts.contacts.clear();
        assertEquals("Reserva confirmada", sender.send(confirmed()).title());
    }

    @Test
    @DisplayName("un mensaje escrito por el administrador sale tal cual")
    void adminMessagesAreNotTranslated() {
        contacts.withLanguage(ANA, "en");
        Notification saved = sender.send(new SendNotificationCommand(ANA, NotificationTypeCode.SYSTEM_MESSAGE,
                "Cierre por lluvia", "Hoy cerramos a las 3 p. m.", null, null));
        assertEquals("Cierre por lluvia", saved.title());
    }

    @Test
    @DisplayName("los fragmentos opcionales también se traducen")
    void nestedFragments() {
        LocalizedText text = LocalizedText.of("booking.cancelled.message").with("code", " RES-1")
                .with("reason", LocalizedText.of("fragment.reason").with("reason", "Lluvia"));
        assertEquals("Your booking RES-1 was cancelled. Reason: Lluvia.", NotificationTexts.defaults().render(text, "en"));
        assertEquals("Tu reserva RES-1 fue cancelada.", NotificationTexts.defaults().render(
                LocalizedText.of("booking.cancelled.message").with("code", " RES-1"), "es"));
    }

    @Test
    @DisplayName("los 4 idiomas tienen las mismas plantillas con los mismos datos")
    void everyLanguageHasEveryTemplate() throws IOException {
        Properties es = load("es");
        for (String lang : NotificationTexts.LANGUAGES) {
            Properties other = load(lang);
            assertEquals(new TreeSet<>(es.stringPropertyNames()), new TreeSet<>(other.stringPropertyNames()),
                    "llaves de " + lang);
            for (String key : es.stringPropertyNames()) {
                if (!key.startsWith("format.")) {
                    assertEquals(params(es.getProperty(key)), params(other.getProperty(key)), lang + " " + key);
                }
            }
        }
    }

    private static Properties load(String lang) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = LocalizedNotificationsTest.class.getResourceAsStream("/i18n/notifications_" + lang + ".properties")) {
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties;
    }

    private static Set<String> params(String template) {
        Set<String> names = new TreeSet<>();
        Matcher matcher = Pattern.compile("\\{([A-Za-z0-9_]+)}").matcher(template);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }
}
