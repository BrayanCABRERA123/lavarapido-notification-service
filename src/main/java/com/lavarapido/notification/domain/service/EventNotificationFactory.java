package com.lavarapido.notification.domain.service;

import com.lavarapido.notification.domain.model.DomainEventEnvelope;
import com.lavarapido.notification.domain.model.NotificationTypeCode;
import com.lavarapido.notification.domain.port.in.SendNotificationCommand;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Decide qué notificaciones genera cada evento de dominio y con qué texto (RF-018).
 *
 * El texto se guarda ya escrito (06-data): la tabla no tiene idioma, así que se escribe en
 * español, que es el idioma por defecto del sistema. El frontend recibe también el code del tipo
 * y puede mostrar su propia traducción.
 *
 * Contrato del payload (ADR-011): cada evento que genera notificación trae el user_id de quien
 * la recibe (customerUserId / operatorUserId / userId). Este servicio no consulta otras bases ni
 * llama a otros servicios para averiguarlo.
 */
public final class EventNotificationFactory {

    private static final ZoneId COLOMBIA = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm");
    private static final String BOOKING = "booking";
    private static final String PAYMENT = "payment";

    /** Lista vacía = el evento no genera notificaciones (o le faltan datos para hacerlo). */
    public List<SendNotificationCommand> from(DomainEventEnvelope event) {
        Map<String, Object> p = event.payload();
        List<SendNotificationCommand> commands = new ArrayList<>();

        switch (event.eventType() == null ? "" : event.eventType()) {
            case "UserRegistered" -> {
                // la bienvenida también va por correo: el evento trae el correo (ADR-011)
                Long user = userId(p, "userId");
                if (user != null) {
                    String name = text(p, "firstName");
                    commands.add(new SendNotificationCommand(user, NotificationTypeCode.USER_WELCOME,
                            name == null ? "¡Bienvenido a LavaRápido!" : "¡Bienvenido a LavaRápido, " + name + "!",
                            welcomeMessage(p), null, null, email(p), name));
                }
            }
            case "BookingCreated" -> add(commands, userId(p, "customerUserId"), NotificationTypeCode.BOOKING_CREATED,
                    "Reserva registrada",
                    "Recibimos tu reserva" + bookingCode(p) + " para el " + when(p) + ". Te avisaremos cuando quede confirmada.",
                    BOOKING, id(p, "bookingId"));
            case "BookingConfirmed" -> {
                add(commands, userId(p, "customerUserId"), NotificationTypeCode.BOOKING_CONFIRMED,
                        "Reserva confirmada",
                        "Tu reserva" + bookingCode(p) + " quedó confirmada para el " + when(p) + ". Te esperamos en la sede.",
                        BOOKING, id(p, "bookingId"));
                // el operario elegido también se entera, si el evento lo trae (domain-events.md)
                add(commands, userId(p, "operatorUserId"), NotificationTypeCode.OPERATOR_ASSIGNED,
                        "Nuevo servicio asignado",
                        "Tienes un servicio" + bookingCode(p) + " programado para el " + when(p) + ".",
                        BOOKING, id(p, "bookingId"));
            }
            case "BookingCancelled" -> add(commands, userId(p, "customerUserId"), NotificationTypeCode.BOOKING_CANCELLED,
                    "Reserva cancelada",
                    "Tu reserva" + bookingCode(p) + " fue cancelada." + reason(p),
                    BOOKING, id(p, "bookingId"));
            case "OperatorAssigned" -> {
                add(commands, userId(p, "operatorUserId"), NotificationTypeCode.OPERATOR_ASSIGNED,
                        "Nuevo servicio asignado",
                        "Te asignaron el servicio" + bookingCode(p) + " para el " + when(p) + ".",
                        BOOKING, id(p, "bookingId"));
                add(commands, userId(p, "customerUserId"), NotificationTypeCode.OPERATOR_ASSIGNED,
                        "Operario asignado",
                        "Ya hay un operario asignado a tu reserva" + bookingCode(p) + ".",
                        BOOKING, id(p, "bookingId"));
            }
            case "ServiceStarted" -> add(commands, userId(p, "customerUserId"), NotificationTypeCode.SERVICE_STARTED,
                    "Tu lavado comenzó",
                    "El operario empezó a lavar tu vehículo" + bookingCode(p) + ".",
                    BOOKING, id(p, "bookingId"));
            case "ServiceCompleted" -> add(commands, userId(p, "customerUserId"), NotificationTypeCode.SERVICE_COMPLETED,
                    "¡Tu vehículo está listo!",
                    "Terminamos el servicio" + bookingCode(p) + ". Ya puedes recogerlo y calificar la atención.",
                    BOOKING, id(p, "bookingId"));
            case "PaymentConfirmed" -> add(commands, userId(p, "customerUserId"), NotificationTypeCode.PAYMENT_CONFIRMED,
                    "Pago aprobado",
                    "Aprobamos tu pago" + amount(p) + ". ¡Gracias!",
                    PAYMENT, id(p, "paymentId"));
            case "PaymentRejected" -> add(commands, userId(p, "customerUserId"), NotificationTypeCode.PAYMENT_REJECTED,
                    "Pago rechazado",
                    "No pudimos aprobar tu pago" + amount(p) + "." + reason(p) + " Puedes intentarlo de nuevo.",
                    PAYMENT, id(p, "paymentId"));
            case "PromotionRedeemed" -> add(commands, userId(p, "customerUserId"), NotificationTypeCode.PROMOTION_REDEEMED,
                    "Cupón canjeado: " + promotionName(p),
                    "Canjeaste el cupón " + promotionName(p) + " en tu reserva" + bookingCode(p) + discountAmount(p) + ".",
                    BOOKING, id(p, "bookingId"));
            default -> {
                // eventos que no generan notificación (UserAuthenticated, RatingSubmitted...)
            }
        }
        return commands;
    }

    private static void add(List<SendNotificationCommand> commands, Long userId, NotificationTypeCode type,
                            String title, String message, String referenceEntity, Long referenceId) {
        if (userId == null) {
            return;
        }
        // sin id no hay referencia completa: la notificación va sin enlace
        boolean hasReference = referenceEntity != null && referenceId != null;
        commands.add(new SendNotificationCommand(userId, type, title, message,
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
    private static String welcomeMessage(Map<String, Object> payload) {
        List<String> roles = roles(payload);
        String start = roles.contains("ADMIN")
                ? "Tu cuenta de administrador quedó creada. Desde el panel gestionas reservas, operarios, pagos y la configuración del lavadero."
                : roles.contains("OPERATOR")
                ? "Ya haces parte del equipo de LavaRápido como operario. En la app verás los servicios que te asignen y podrás iniciarlos y finalizarlos."
                : "Tu cuenta quedó creada. Ya puedes registrar tus vehículos y reservar tu primer lavado.";
        if (!Boolean.TRUE.equals(payload.get("createdByAdmin"))) {
            return start;
        }
        return start + " Un administrador creó tu cuenta con este correo: para entrar, pídele tu contraseña"
                + " o usa \"¿Olvidaste tu contraseña?\" en el inicio de sesión para crear una nueva.";
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

    private static String reason(Map<String, Object> payload) {
        Object reason = payload.get("reason");
        return reason instanceof String text && !text.isBlank() ? " Motivo: " + text.strip() + "." : "";
    }

    private static String amount(Map<String, Object> payload) {
        Object value = payload.get("amount");
        if (value instanceof Number number) {
            // pesos colombianos sin decimales: 45000 -> $45.000
            String digits = String.format("%,d", Math.round(number.doubleValue())).replace(',', '.');
            return " de $" + digits;
        }
        return "";
    }

    private static String promotionName(Map<String, Object> payload) {
        String name = text(payload, "promotionName");
        return name == null ? "tu promoción" : name;
    }

    private static String discountAmount(Map<String, Object> payload) {
        Object value = payload.get("discountAmount");
        if (value instanceof Number number) {
            String digits = String.format("%,d", Math.round(number.doubleValue())).replace(',', '.');
            return ": te descontamos $" + digits;
        }
        return "";
    }

    private static String when(Map<String, Object> payload) {
        Object value = payload.get("scheduledStart");
        if (value instanceof String text) {
            try {
                return formatWhen(Instant.parse(text));
            } catch (DateTimeParseException ignored) {
                // se usa el texto genérico de abajo
            }
        }
        return formatWhen(null);
    }

    /** "08/10/2026 a las 07:30" en hora de Colombia; sin hora, un texto genérico. */
    static String formatWhen(Instant start) {
        return start == null ? "horario reservado" : DATE_TIME.format(start.atZone(COLOMBIA));
    }
}
