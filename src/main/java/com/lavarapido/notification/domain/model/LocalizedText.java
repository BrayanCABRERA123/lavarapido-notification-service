package com.lavarapido.notification.domain.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Un texto de notificación sin idioma todavía: la llave de la plantilla y sus datos. Se arma en el
 * idioma de quien lo recibe justo antes de enviarlo (NotificationTexts), así el mismo aviso le
 * llega en español a uno y en inglés a otro.
 *
 * Los parámetros pueden ser texto, números, fechas (Instant, se escriben en la hora del lavadero y
 * con el formato de cada idioma) u otro LocalizedText (por ejemplo, " Motivo: ..." que solo va si
 * hay motivo). null se escribe vacío.
 */
public record LocalizedText(String key, Map<String, Object> params) {

    public LocalizedText {
        params = params == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(params));
    }

    public static LocalizedText of(String key) {
        return new LocalizedText(key, Map.of());
    }

    /** Copia con un parámetro más. */
    public LocalizedText with(String name, Object value) {
        Map<String, Object> next = new LinkedHashMap<>(params);
        next.put(name, value);
        return new LocalizedText(key, next);
    }
}
