package com.lavarapido.notification.infrastructure.adapter.out.persistence;

import com.lavarapido.notification.domain.model.BookingReminder;
import com.lavarapido.notification.domain.port.out.BookingReminderRepository;
import com.lavarapido.notification.infrastructure.adapter.out.persistence.entity.BookingReminderJpaEntity;
import com.lavarapido.notification.infrastructure.adapter.out.persistence.repository.BookingReminderJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Recordatorios en notification.booking_reminder. Hay una fila por reserva y anticipación
 * (uq_booking_reminder_lead), así que reprogramar reutiliza la fila en vez de crear otra.
 */
@Component
class BookingReminderPersistenceAdapter implements BookingReminderRepository {

    private final BookingReminderJpaRepository reminders;

    BookingReminderPersistenceAdapter(BookingReminderJpaRepository reminders) {
        this.reminders = reminders;
    }

    @Override
    public void replaceForBooking(long bookingId, List<BookingReminder> wanted, Instant now) {
        Map<Integer, BookingReminderJpaEntity> existing = reminders.findByBookingId(bookingId).stream()
                .collect(Collectors.toMap(BookingReminderJpaEntity::getLeadMinutes, Function.identity()));
        Set<Integer> wantedLeads = wanted.stream().map(BookingReminder::leadMinutes).collect(Collectors.toSet());

        for (BookingReminderJpaEntity row : existing.values()) {
            if (!wantedLeads.contains(row.getLeadMinutes()) && row.getDeletedAt() == null && row.getSentAt() == null) {
                row.cancel(now);
                reminders.save(row);
            }
        }
        for (BookingReminder reminder : wanted) {
            BookingReminderJpaEntity row = existing.getOrDefault(reminder.leadMinutes(),
                    new BookingReminderJpaEntity(bookingId, reminder.leadMinutes(), now));
            row.schedule(reminder.userId(), reminder.bookingCode(), reminder.scheduledStart(), reminder.remindAt(), now);
            reminders.save(row);
        }
    }

    @Override
    public void cancelForBooking(long bookingId, Instant now) {
        for (BookingReminderJpaEntity row : reminders.findByBookingId(bookingId)) {
            if (row.getDeletedAt() == null && row.getSentAt() == null) {
                row.cancel(now);
                reminders.save(row);
            }
        }
    }

    @Override
    public List<BookingReminder> findDue(Instant now, int limit) {
        return reminders.findDue(now, PageRequest.of(0, limit)).stream()
                .map(row -> new BookingReminder(row.getId(), row.getBookingId(), row.getUserId(), row.getBookingCode(),
                        row.getScheduledStart(), row.getLeadMinutes(), row.getRemindAt(), row.getSentAt()))
                .toList();
    }

    @Override
    public void markSent(long reminderId, Instant sentAt) {
        reminders.findById(reminderId).ifPresent(row -> {
            row.markSent(sentAt);
            reminders.save(row);
        });
    }
}
