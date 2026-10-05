package com.lavarapido.notification.application.usecase;

import com.lavarapido.notification.domain.model.BookingReminder;
import com.lavarapido.notification.domain.model.NotificationTypeCode;
import com.lavarapido.notification.domain.port.in.BookingReminderUseCase;
import com.lavarapido.notification.domain.port.in.SendNotificationCommand;
import com.lavarapido.notification.domain.port.in.SendNotificationUseCase;
import com.lavarapido.notification.domain.port.out.BookingReminderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Recordatorios de reserva (categoría REMINDER, tipo BOOKING_REMINDER).
 *
 * Se programan con los eventos de booking-service, sin consultar sus reservas: BookingConfirmed
 * trae el cliente y la hora; BookingCancelled los cancela. Una tarea programada llama a
 * sendDue() y cada recordatorio llega a la bandeja, por push y por correo (el correo se pide a
 * security-service en el momento, ADR-011 sección 8).
 */
@Service
public class BookingReminderService implements BookingReminderUseCase {

    private static final Logger log = LoggerFactory.getLogger(BookingReminderService.class);
    private static final String BOOKING = "booking";

    private final BookingReminderRepository reminders;
    private final SendNotificationUseCase sender;
    private final ReminderSettings settings;
    private final EmailRecipients emailRecipients;
    private final Clock clock;

    public BookingReminderService(BookingReminderRepository reminders, SendNotificationUseCase sender,
                                  ReminderSettings settings, EmailRecipients emailRecipients, Clock clock) {
        this.reminders = reminders;
        this.sender = sender;
        this.settings = settings;
        this.emailRecipients = emailRecipients;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void schedule(long bookingId, long userId, String bookingCode, Instant scheduledStart) {
        Instant now = clock.instant();
        List<BookingReminder> planned = BookingReminder.planFor(bookingId, userId, bookingCode, scheduledStart,
                settings.leads(), now);
        reminders.replaceForBooking(bookingId, planned, now);
        log.info("Booking {}: {} reminder(s) scheduled", bookingId, planned.size());
    }

    @Override
    @Transactional
    public void cancel(long bookingId) {
        reminders.cancelForBooking(bookingId, clock.instant());
        log.info("Booking {}: pending reminders cancelled", bookingId);
    }

    /**
     * Envía los vencidos. Un recordatorio de una cita que ya pasó (el servicio estuvo apagado)
     * se marca como atendido sin avisar: un "tu lavado es a las 9" a las 11 solo confunde.
     */
    @Override
    @Transactional
    public int sendDue() {
        Instant now = clock.instant();
        int sent = 0;
        for (BookingReminder reminder : reminders.findDue(now, settings.batchSize())) {
            if (!reminder.isStale(now)) {
                sender.send(emailRecipients.withEmail(new SendNotificationCommand(reminder.userId(),
                        NotificationTypeCode.BOOKING_REMINDER, reminder.title(), reminder.message(), BOOKING,
                        reminder.bookingId())));
                sent++;
            }
            reminders.markSent(reminder.id(), now);
        }
        if (sent > 0) {
            log.info("{} booking reminder(s) sent", sent);
        }
        return sent;
    }
}
