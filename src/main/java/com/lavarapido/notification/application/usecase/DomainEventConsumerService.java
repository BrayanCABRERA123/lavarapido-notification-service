package com.lavarapido.notification.application.usecase;

import com.lavarapido.notification.domain.model.BookingEventPayload;
import com.lavarapido.notification.domain.model.DomainEventEnvelope;
import com.lavarapido.notification.domain.port.in.BookingReminderUseCase;
import com.lavarapido.notification.domain.port.in.ConsumeDomainEventUseCase;
import com.lavarapido.notification.domain.port.in.SendNotificationCommand;
import com.lavarapido.notification.domain.port.in.SendNotificationUseCase;
import com.lavarapido.notification.domain.port.out.ProcessedEventRepository;
import com.lavarapido.notification.domain.service.EventNotificationFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Convierte los eventos de los otros servicios en notificaciones y, con los de reserva, programa
 * o cancela los recordatorios.
 *
 * Idempotente (cross-cutting.md §7): el eventId se registra en la misma transacción que las
 * notificaciones y los recordatorios, así un evento repetido no los duplica y uno que falló se
 * puede reintentar.
 */
@Service
public class DomainEventConsumerService implements ConsumeDomainEventUseCase {

    private static final Logger log = LoggerFactory.getLogger(DomainEventConsumerService.class);
    private static final String BOOKING_CONFIRMED = "BookingConfirmed";
    private static final String BOOKING_CANCELLED = "BookingCancelled";

    private final ProcessedEventRepository processedEvents;
    private final SendNotificationUseCase sender;
    private final EventNotificationFactory factory;
    private final BookingReminderUseCase reminders;
    private final EmailRecipients emailRecipients;
    private final StaffNotificationService staff;

    public DomainEventConsumerService(ProcessedEventRepository processedEvents, SendNotificationUseCase sender,
                                      EventNotificationFactory factory, BookingReminderUseCase reminders,
                                      EmailRecipients emailRecipients, StaffNotificationService staff) {
        this.processedEvents = processedEvents;
        this.sender = sender;
        this.factory = factory;
        this.reminders = reminders;
        this.emailRecipients = emailRecipients;
        this.staff = staff;
    }

    @Override
    @Transactional
    public void handle(DomainEventEnvelope event) {
        if (event.eventId() == null || event.eventId().isBlank()) {
            log.warn("Event {} without eventId ignored: it cannot be deduplicated", event.eventType());
            return;
        }
        if (processedEvents.exists(event.eventId())) {
            log.info("Event {} ({}) already processed, ignoring the duplicate", event.eventId(), event.eventType());
            return;
        }

        // las de reserva también van por correo (ADR-011, sección 8)
        List<SendNotificationCommand> commands = factory.from(event).stream().map(emailRecipients::withEmail).toList();
        sender.sendAll(commands);
        updateReminders(event);
        // operario y administrador (RF-018): sus propios avisos, sin correo
        int staffNotifications = staff.handle(event);
        processedEvents.record(event.eventId(), event.eventType());
        log.info("Event {} ({}) produced {} notification(s) and {} for staff", event.eventId(), event.eventType(),
                commands.size(), staffNotifications);
    }

    /** Una reserva confirmada (o reprogramada) programa recordatorios; una cancelada los quita. */
    private void updateReminders(DomainEventEnvelope event) {
        if (BOOKING_CONFIRMED.equals(event.eventType())) {
            BookingEventPayload.from(event)
                    .filter(BookingEventPayload::canBeReminded)
                    .ifPresent(booking -> reminders.schedule(booking.bookingId(), booking.customerUserId(),
                            booking.bookingCode(), booking.scheduledStart()));
        } else if (BOOKING_CANCELLED.equals(event.eventType())) {
            BookingEventPayload.from(event).ifPresent(booking -> reminders.cancel(booking.bookingId()));
        }
    }
}
