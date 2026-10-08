package com.lavarapido.notification.domain.service;

import com.lavarapido.notification.domain.model.BookingEventPayload;
import com.lavarapido.notification.domain.model.DomainEventEnvelope;
import com.lavarapido.notification.domain.model.NotificationTypeCode;
import com.lavarapido.notification.domain.model.TrackedBooking;
import com.lavarapido.notification.domain.port.in.SendNotificationCommand;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Avisos para el personal (RF-018), además de los del cliente que arma EventNotificationFactory:
 *
 * <ul>
 *   <li>Operario: su servicio se cancela, cambia de hora o se le asigna a otro operario.</li>
 *   <li>Administrador: reserva nueva, reprogramada o cancelada (con el motivo). El evento no dice
 *       quién canceló, así que también le llega la que cancela otro administrador.</li>
 * </ul>
 *
 * Usa lo último que se supo de la reserva ({@link TrackedBooking}) para distinguir una reserva
 * nueva de una reprogramada y para saber a qué operario avisarle. Son tipos propios del personal:
 * van a la bandeja y por push, nunca por correo (EmailRecipients no los incluye).
 */
public final class StaffNotificationFactory {

    private static final String BOOKING = "booking";
    private static final String BOOKING_CONFIRMED = "BookingConfirmed";
    private static final String BOOKING_CANCELLED = "BookingCancelled";
    private static final String OPERATOR_ASSIGNED = "OperatorAssigned";

    /** Avisos que genera el evento, comparándolo con lo que ya se sabía de la reserva. */
    public List<SendNotificationCommand> from(DomainEventEnvelope event, Optional<TrackedBooking> previous,
                                              List<Long> adminIds) {
        List<SendNotificationCommand> commands = new ArrayList<>();
        Optional<BookingEventPayload> payload = BookingEventPayload.from(event);
        if (payload.isEmpty()) {
            return commands;
        }
        BookingEventPayload booking = payload.get();
        String code = codeOf(booking, previous);
        long bookingId = booking.bookingId();

        switch (event.eventType() == null ? "" : event.eventType()) {
            case BOOKING_CONFIRMED -> {
                if (previous.isEmpty() || previous.get().cancelled()) {
                    forEach(commands, adminIds, NotificationTypeCode.ADMIN_BOOKING_CREATED, "Nueva reserva",
                            "Se agendó la reserva" + code + " para el " + EventNotificationFactory.formatWhen(booking.scheduledStart()) + ".",
                            bookingId);
                } else if (previous.get().startChangesTo(booking.scheduledStart())) {
                    String newTime = EventNotificationFactory.formatWhen(booking.scheduledStart());
                    forEach(commands, adminIds, NotificationTypeCode.ADMIN_BOOKING_RESCHEDULED, "Reserva reprogramada",
                            "La reserva" + code + " pasó del " + EventNotificationFactory.formatWhen(previous.get().scheduledStart())
                                    + " al " + newTime + ".",
                            bookingId);
                    add(commands, previous.get().operatorUserId(), NotificationTypeCode.OPERATOR_SERVICE_RESCHEDULED,
                            "Servicio reprogramado", "Tu servicio" + code + " ahora es el " + newTime + ".", bookingId);
                }
            }
            case BOOKING_CANCELLED -> {
                Instant start = booking.scheduledStart() != null ? booking.scheduledStart()
                        : previous.map(TrackedBooking::scheduledStart).orElse(null);
                String when = EventNotificationFactory.formatWhen(start);
                // booking-service manda en "reason" el nombre del motivo (ej. "El cliente la canceló")
                String reason = text(event.payload(), "reason");
                forEach(commands, adminIds, NotificationTypeCode.ADMIN_BOOKING_CANCELLED, "Reserva cancelada",
                        "Se canceló la reserva" + code + " del " + when + "."
                                + (reason == null ? "" : " Motivo: " + reason + ".") + " El espacio quedó libre.",
                        bookingId);
                previous.filter(p -> !p.cancelled()).ifPresent(p -> add(commands, p.operatorUserId(),
                        NotificationTypeCode.OPERATOR_SERVICE_CANCELLED, "Servicio cancelado",
                        "Se canceló el servicio" + code + " del " + when + ". Ya no tienes que atenderlo.",
                        bookingId));
            }
            case OPERATOR_ASSIGNED -> {
                Long newOperator = id(event.payload(), "operatorUserId");
                previous.map(TrackedBooking::operatorUserId)
                        .filter(old -> !old.equals(newOperator))
                        .ifPresent(old -> add(commands, old, NotificationTypeCode.OPERATOR_SERVICE_UNASSIGNED,
                                "Servicio reasignado",
                                "El servicio" + code + " del " + EventNotificationFactory.formatWhen(
                                        booking.scheduledStart() != null ? booking.scheduledStart()
                                                : previous.get().scheduledStart())
                                        + " se asignó a otro operario. Ya no tienes que atenderlo.",
                                bookingId));
            }
            default -> {
                // los demás eventos no le avisan nada al personal
            }
        }
        return commands;
    }

    /** Cómo queda la reserva después del evento; vacío si el evento no la cambia. */
    public Optional<TrackedBooking> track(DomainEventEnvelope event, Optional<TrackedBooking> previous) {
        Optional<BookingEventPayload> payload = BookingEventPayload.from(event);
        if (payload.isEmpty()) {
            return Optional.empty();
        }
        BookingEventPayload booking = payload.get();
        return switch (event.eventType() == null ? "" : event.eventType()) {
            case BOOKING_CONFIRMED -> Optional.of(previous
                    .filter(p -> !p.cancelled())
                    .map(p -> p.rescheduled(booking.bookingCode(), booking.scheduledStart()))
                    .orElseGet(() -> TrackedBooking.confirmed(booking.bookingId(), booking.bookingCode(),
                            booking.customerUserId(), booking.scheduledStart())));
            case BOOKING_CANCELLED -> Optional.of(previous
                    .orElseGet(() -> TrackedBooking.confirmed(booking.bookingId(), booking.bookingCode(),
                            booking.customerUserId(), booking.scheduledStart()))
                    .cancel());
            // una reserva de antes de esta tabla también queda registrada con su operario
            case OPERATOR_ASSIGNED -> Optional.of(previous
                    .orElseGet(() -> TrackedBooking.confirmed(booking.bookingId(), booking.bookingCode(),
                            booking.customerUserId(), booking.scheduledStart()))
                    .assignedTo(id(event.payload(), "operatorUserId")));
            default -> Optional.empty();
        };
    }

    private static String codeOf(BookingEventPayload booking, Optional<TrackedBooking> previous) {
        String code = booking.bookingCode() != null ? booking.bookingCode()
                : previous.map(TrackedBooking::bookingCode).orElse(null);
        return code == null ? "" : " " + code;
    }

    private static void forEach(List<SendNotificationCommand> commands, List<Long> userIds, NotificationTypeCode type,
                                String title, String message, long bookingId) {
        userIds.stream().distinct().forEach(userId -> add(commands, userId, type, title, message, bookingId));
    }

    private static void add(List<SendNotificationCommand> commands, Long userId, NotificationTypeCode type,
                            String title, String message, long bookingId) {
        if (userId == null || userId <= 0) {
            return;
        }
        commands.add(new SendNotificationCommand(userId, type, title, message, BOOKING, bookingId));
    }

    private static Long id(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof Number number) {
            return number.longValue() > 0 ? number.longValue() : null;
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
}
