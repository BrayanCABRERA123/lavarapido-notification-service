package com.lavarapido.notification.domain.service;

import com.lavarapido.notification.domain.model.LocalizedText;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plantillas de las notificaciones en los 4 idiomas de la app (RF-020): español, inglés, francés y
 * portugués (i18n/notifications_xx.properties). Las llaves que falten en un idioma salen en español.
 *
 * Una plantilla usa {nombre} para cada parámetro de LocalizedText. Las fechas se escriben en la hora
 * del lavadero con el formato del idioma (llave format.when).
 */
public final class NotificationTexts {

    public static final String DEFAULT_LANGUAGE = "es";
    public static final List<String> LANGUAGES = List.of("es", "en", "fr", "pt");
    private static final ZoneId SHOP_ZONE = ZoneId.of("America/Bogota");
    private static final Pattern PARAM = Pattern.compile("\\{([A-Za-z0-9_]+)}");
    private static final NotificationTexts DEFAULTS = new NotificationTexts();

    private final Map<String, Properties> bundles = new ConcurrentHashMap<>();

    /** Las plantillas del classpath; se cargan una vez. */
    public static NotificationTexts defaults() {
        return DEFAULTS;
    }

    /** "EN", "en-US" o null -> "en" / "es". Un idioma que la app no tiene sale en español. */
    public static String language(String code) {
        if (code == null || code.isBlank()) {
            return DEFAULT_LANGUAGE;
        }
        String lang = code.strip().toLowerCase(Locale.ROOT);
        lang = lang.length() > 2 ? lang.substring(0, 2) : lang;
        return LANGUAGES.contains(lang) ? lang : DEFAULT_LANGUAGE;
    }

    public String render(LocalizedText text, String languageCode) {
        if (text == null) {
            return "";
        }
        String lang = language(languageCode);
        String template = template(text.key(), lang);
        Matcher matcher = PARAM.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            Object value = text.params().get(matcher.group(1));
            matcher.appendReplacement(out, Matcher.quoteReplacement(valueOf(value, lang)));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** La fecha y hora de una reserva en el formato del idioma; sin fecha, el texto genérico. */
    public String when(Instant instant, String languageCode) {
        String lang = language(languageCode);
        if (instant == null) {
            return template("format.whenUnknown", lang);
        }
        DateTimeFormatter format = DateTimeFormatter.ofPattern(template("format.when", lang), Locale.forLanguageTag(lang));
        return format.format(instant.atZone(SHOP_ZONE));
    }

    private String valueOf(Object value, String lang) {
        if (value == null) {
            return "";
        }
        if (value instanceof LocalizedText nested) {
            return render(nested, lang);
        }
        if (value instanceof Instant instant) {
            return when(instant, lang);
        }
        return String.valueOf(value);
    }

    private String template(String key, String lang) {
        String value = bundle(lang).getProperty(key);
        if (value == null && !DEFAULT_LANGUAGE.equals(lang)) {
            value = bundle(DEFAULT_LANGUAGE).getProperty(key);
        }
        if (value == null) {
            throw new IllegalArgumentException("Missing notification text: " + key);
        }
        return value;
    }

    private Properties bundle(String lang) {
        return bundles.computeIfAbsent(lang, NotificationTexts::load);
    }

    private static Properties load(String lang) {
        String path = "/i18n/notifications_" + lang + ".properties";
        Properties properties = new Properties();
        try (InputStream in = NotificationTexts.class.getResourceAsStream(path)) {
            if (in != null) {
                properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + path, e);
        }
        return properties;
    }
}
