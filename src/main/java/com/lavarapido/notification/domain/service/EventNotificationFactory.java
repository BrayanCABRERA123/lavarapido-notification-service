package com.lavarapido.notification.domain.service;

import com.lavarapido.notification.domain.model.DomainEventEnvelope;
import com.lavarapido.notification.domain.model.LocalizedText;
import com.lavarapido.notification.domain.model.NotificationTypeCode;
import com.lavarapido.notification.domain.port.in.SendNotificationCommand;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Decide qué notificaciones genera cada evento de dominio y con qué texto (RF-018).
 *
 * Cada texto es una plantilla (NotificationTexts, RF-020): el aviso se guarda ya escrito en el
 * idioma de la cuenta de quien lo recibe, que se conoce justo antes de enviarlo. Aquí se arma en
 * español, el idioma por defecto del sistema.
 *
 * Contrato del payload (ADR-011): cada evento que genera notificación trae el user_id de quien
 * la recibe (customerUserId / operatorUserId / userId). Este servicio no consulta otras bases ni
 * llama a otros servicios para averiguarlo.
 */
public final class EventNotificationFactory {

    private static final String BOOKING = "booking";
    private static final String PAYMENT = "payment";
    private static final int MAX_AREAS = 5;
    private static final int MAX_AREA_LENGTH = 30;

    /** Dirección de la web: el aviso del informe de inspección lleva el enlace público. */
    private final String webUrl;

    public EventNotificationFactory() {
        this("http://localhost:4200");
    }

    public EventNotificationFactory(String webUrl) {
        this.webUrl = webUrl == null ? "" : webUrl.replaceAll("/+$", "");
    }

    /** Lista vacía = el evento no genera notificaciones (o le faltan datos para hacerlo). */
    public List<SendNotificationCommand> from(DomainEventEnvelope event) {
        Map<String, Object> p = event.payload();
        List<SendNotificationCommand> commands = new ArrayList<>();
        Long bookingId = id(p, "bookingId");

        switch (event.eventType() == null ? "" : event.eventType()) {
            case "UserRegistered" -> {
                // la bienvenida también va por correo: el evento trae el correo (ADR-011)
                Long user = userId(p, "userId");
                if (user != null) {
                    String name = text(p, "firstName");
                    LocalizedText title = name == null ? t("welcome.title") : t("welcome.titleName").with("name", name);
                    commands.add(SendNotificationCommand.localized(user, NotificationTypeCode.USER_WELCOME, title,
                            welcomeMessage(p), null, null).withEmail(email(p), name));
                }
            }
            case "BookingCreated" -> add(commands, userId(p, "customerUserId"), NotificationTypeCode.BOOKING_CREATED,
                    "booking.created", booking(p), BOOKING, bookingId);
            case "BookingConfirmed" -> {
                add(commands, userId(p, "customerUserId"), NotificationTypeCode.BOOKING_CONFIRMED,
                        "booking.confirmed", booking(p), BOOKING, bookingId);
                // el operario elegido también se entera, si el evento lo trae (domain-events.md)
                add(commands, userId(p, "operatorUserId"), NotificationTypeCode.OPERATOR_ASSIGNED,
                        "operator.scheduled", booking(p), BOOKING, bookingId);
            }
            case "BookingCancelled" -> add(commands, userId(p, "customerUserId"), NotificationTypeCode.BOOKING_CANCELLED,
                    "booking.cancelled", booking(p).with("reason", reason(p)), BOOKING, bookingId);
            // A-11: el administrador cambió el estado de la reserva desde su panel
            case "BookingStatusChanged" -> {
                String status = text(p, "status");
                if ("IN_PROGRESS".equals(status)) {
                    add(commands, userId(p, "customerUserId"), NotificationTypeCode.SERVICE_STARTED,
                            "service.started", booking(p), BOOKING, bookingId);
                } else if ("COMPLETED".equals(status)) {
                    add(commands, userId(p, "customerUserId"), NotificationTypeCode.SERVICE_COMPLETED,
                            "service.completed", booking(p), BOOKING, bookingId);
                } else if ("NO_SHOW".equals(status)) {
                    add(commands, userId(p, "customerUserId"), NotificationTypeCode.BOOKING_CANCELLED,
                            "booking.noShow", booking(p), BOOKING, bookingId);
                }
            }
            case "OperatorAssigned" -> {
                add(commands, userId(p, "operatorUserId"), NotificationTypeCode.OPERATOR_ASSIGNED,
                        "operator.assigned", booking(p), BOOKING, bookingId);
                add(commands, userId(p, "customerUserId"), NotificationTypeCode.OPERATOR_ASSIGNED,
                        "client.operatorAssigned", booking(p), BOOKING, bookingId);
            }
            case "ServiceStarted" -> add(commands, userId(p, "customerUserId"), NotificationTypeCode.SERVICE_STARTED,
                    "service.started", booking(p), BOOKING, bookingId);
            case "ServiceCompleted" -> add(commands, userId(p, "customerUserId"), NotificationTypeCode.SERVICE_COMPLETED,
                    "service.completed", booking(p), BOOKING, bookingId);
            case "PaymentConfirmed" -> add(commands, userId(p, "customerUserId"), NotificationTypeCode.PAYMENT_CONFIRMED,
                    "payment.confirmed", payment(p), PAYMENT, id(p, "paymentId"));
            case "PaymentRejected" -> add(commands, userId(p, "customerUserId"), NotificationTypeCode.PAYMENT_REJECTED,
                    "payment.rejected", payment(p).with("reason", reason(p)), PAYMENT, id(p, "paymentId"));
            case "PaymentRefunded" -> add(commands, userId(p, "customerUserId"), NotificationTypeCode.PAYMENT_REFUNDED,
                    "payment.refunded", payment(p), PAYMENT, id(p, "paymentId"));
            case "PromotionRedeemed" -> {
                String name = text(p, "promotionName");
                Object promotion = name == null ? t("promotion.defaultName") : name;
                Object discount = money(p.get("discountAmount")) == null ? null
                        : t("fragment.discount").with("amount", money(p.get("discountAmount")));
                add(commands, userId(p, "customerUserId"), NotificationTypeCode.PROMOTION_REDEEMED,
                        t("promotion.redeemed.title").with("name", promotion),
                        t("promotion.redeemed.message").with("name", promotion).with("code", bookingCode(p))
                                .with("discount", discount),
                        BOOKING, bookingId);
            }
            case "LoyaltyPointsEarned" -> pointsEarned(commands, p);
            case "InspectionShared" -> add(commands, userId(p, "customerUserId"), NotificationTypeCode.INSPECTION_REPORT,
                    t("inspection.title"), inspectionMessage(p), BOOKING, bookingId);
            default -> {
                // eventos que no generan notificación (UserAuthenticated, RatingSubmitted...)
            }
        }
        return commands;
    }

    /**
     * Puntos que ganó una reserva pagada (payment.loyalty_points_earned) y, por cada promoción que
     * esos puntos desbloquearon, un aviso de cupón canjeable con el código para usarlo al pagar.
     */
    private static void pointsEarned(List<SendNotificationCommand> commands, Map<String, Object> p) {
        Long user = userId(p, "customerUserId");
        Long points = id(p, "points");
        if (user == null || points == null || points <= 0) {
            return;
        }
        Long balance = id(p, "balance");
        add(commands, user, NotificationTypeCode.LOYALTY_POINTS_EARNED,
                t("loyalty.earned.title").with("points", pointsText(points)),
                t("loyalty.earned.message").with("code", bookingCode(p)).with("points", pointsText(points))
                        .with("balance", balance == null ? null : t("fragment.balance").with("points", pointsText(balance))),
                BOOKING, id(p, "bookingId"));

        for (Map<String, Object> promotion : maps(p.get("unlockedPromotions"))) {
            String code = text(promotion, "code");
            if (code == null) {
                continue;
            }
            String name = text(promotion, "name");
            Long percent = id(promotion, "discountPercent");
            add(commands, user, NotificationTypeCode.PROMOTION_AVAILABLE, t("promotion.available.title"),
                    t("promotion.available.message")
                            .with("promotion", name == null ? t("promotion.unnamed") : t("promotion.named").with("name", name))
                            .with("percent", percent == null || percent <= 0 ? null : t("fragment.percent").with("percent", percent))
                            .with("code", code),
                    null, null);
        }
    }

    /**
     * Resumen del informe que el admin compartió (execution.inspection_shared) con el enlace público.
     * Se nombran hasta 5 partes revisadas (cortas) y el resto se cuenta, para que el aviso quepa en
     * los 500 caracteres en cualquier idioma sin cortar el enlace.
     */
    private LocalizedText inspectionMessage(Map<String, Object> p) {
        String plate = text(p, "plate");
        String code = text(p, "bookingCode");
        Long done = id(p, "phasesDone");
        Long total = id(p, "phasesTotal");
        Long findings = id(p, "findings");
        Long major = id(p, "majorFindings");
        String token = text(p, "publicToken");

        Object findingsText;
        if (findings == null || findings == 0) {
            findingsText = t("fragment.noFindings");
        } else {
            List<String> areas = strings(p.get("areas"));
            List<String> named = areas.stream().limit(MAX_AREAS)
                    .map(a -> a.length() > MAX_AREA_LENGTH ? a.substring(0, MAX_AREA_LENGTH - 1) + "…" : a).toList();
            int more = areas.size() - named.size();
            Object areasText = named.isEmpty() ? t("fragment.noAreas") : t("fragment.areas")
                    .with("areas", String.join(", ", named))
                    .with("more", more > 0 ? t("fragment.moreAreas").with("n", more) : null);
            findingsText = t("fragment.findings").with("count", findings)
                    .with("major", major == null || major == 0 ? null : t("fragment.major").with("major", major))
                    .with("areas", areasText);
        }
        return t("inspection.message")
                .with("plate", plate == null ? null : " " + plate)
                .with("code", code == null ? null : t("fragment.inspectionCode").with("code", code))
                .with("phases", done == null || total == null ? null
                        : t("fragment.phases").with("done", done).with("total", total))
                .with("findings", findingsText)
                .with("link", token == null ? null : t("fragment.link").with("url", webUrl + "/report/" + token));
    }

    /** Los datos de reserva que usan casi todos los avisos: el código y la fecha. */
    private static LocalizedText booking(Map<String, Object> p) {
        return new LocalizedText("", Map.of()).with("code", bookingCode(p)).with("when", when(p));
    }

    private static LocalizedText payment(Map<String, Object> p) {
        Object amount = money(p.get("amount"));
        return new LocalizedText("", Map.of()).with("amount", amount == null ? null : t("fragment.amount").with("amount", amount));
    }

    private static LocalizedText t(String key) {
        return LocalizedText.of(key);
    }

    private static LocalizedText pointsText(long points) {
        return points == 1 ? t("points.one") : t("points.many").with("n", points);
    }

    // lista de objetos JSON del payload (ej. unlockedPromotions); lo que no sea un objeto se ignora
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().filter(Map.class::isInstance).map(item -> (Map<String, Object>) item).toList();
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().filter(String.class::isInstance).map(String.class::cast).map(String::strip)
                .filter(s -> !s.isEmpty()).toList();
    }

    /** Aviso con la plantilla "<key>.title" / "<key>.message" y los datos de params. */
    private static void add(List<SendNotificationCommand> commands, Long userId, NotificationTypeCode type,
                            String key, LocalizedText params, String referenceEntity, Long referenceId) {
        add(commands, userId, type, new LocalizedText(key + ".title", params.params()),
                new LocalizedText(key + ".message", params.params()), referenceEntity, referenceId);
    }

    private static void add(List<SendNotificationCommand> commands, Long userId, NotificationTypeCode type,
                            LocalizedText title, LocalizedText message, String referenceEntity, Long referenceId) {
        if (userId == null) {
            return;
        }
        // sin id no hay referencia completa: la notificación va sin enlace
        boolean hasReference = referenceEntity != null && referenceId != null;
        commands.add(SendNotificationCommand.localized(userId, type, title, message,
                hasReference ? referenceEntity : null, hasReference ? referenceId : null));
    }

    private static Long userId(Map<String, Object> payload, String key) {
        Long value = id(payload, key);
        return value != null && value > 0 ? value : null;
    }

    private static Long id(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && text.matches("\\d{1,18}")) {
            return Long.parseLong(text);
        }
        return null;
    }

    private static String text(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value instanceof String text && !text.isBlank() ? text.strip() : null;
    }

    // solo un correo con forma válida; si no, la bienvenida va sin email
    private static String email(Map<String, Object> payload) {
        String value = text(payload, "email");
        return value != null && value.length() <= 254 && value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") ? value : null;
    }

    /**
     * La bienvenida según el rol (gana el de más privilegios, igual que la pantalla de inicio) y
     * según quién abrió la cuenta: si fue un administrador, la persona no eligió su contraseña y
     * se le explica cómo entrar. La contraseña nunca va en la notificación ni en el correo.
     */
    private static LocalizedText welcomeMessage(Map<String, Object> payload) {
        List<String> roles = roles(payload);
        String start = roles.contains("ADMIN") ? "welcome.admin"
                : roles.contains("OPERATOR") ? "welcome.operator" : "welcome.client";
        return t("welcome.message").with("start", t(start))
                .with("createdByAdmin", Boolean.TRUE.equals(payload.get("createdByAdmin")) ? t("welcome.createdByAdmin") : null);
    }

    private static List<String> roles(Map<String, Object> payload) {
        Object value = payload.get("roles");
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
    }

    private static String bookingCode(Map<String, Object> payload) {
        Object code = payload.get("bookingCode");
        return code instanceof String text && !text.isBlank() ? " " + text.strip() : "";
    }

    private static LocalizedText reason(Map<String, Object> payload) {
        Object reason = payload.get("reason");
        return reason instanceof String text && !text.isBlank() ? t("fragment.reason").with("reason", text.strip()) : null;
    }

    /** Pesos colombianos sin decimales: 45000 -> "$45.000" (igual en todos los idiomas). */
    private static String money(Object value) {
        if (value instanceof Number number) {
            return "$" + String.format("%,d", Math.round(number.doubleValue())).replace(',', '.');
        }
        return null;
    }

    private static Object when(Map<String, Object> payload) {
        Object value = payload.get("scheduledStart");
        if (value instanceof String text) {
            try {
                return Instant.parse(text);
            } catch (DateTimeParseException ignored) {
                // se usa el texto genérico de abajo
            }
        }
        return when((Instant) null);
    }

    /** La fecha de una reserva como parámetro: sin hora, el texto genérico ("horario reservado"). */
    static Object when(Instant start) {
        return start == null ? t("format.whenUnknown") : start;
    }

    /** "08/10/2026 a las 07:30" en hora de Colombia; sin hora, un texto genérico. */
    static String formatWhen(Instant start) {
        return NotificationTexts.defaults().when(start, NotificationTexts.DEFAULT_LANGUAGE);
    }
}
