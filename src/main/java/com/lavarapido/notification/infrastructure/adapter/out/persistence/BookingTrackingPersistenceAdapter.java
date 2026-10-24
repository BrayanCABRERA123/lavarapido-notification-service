package com.lavarapido.notification.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import com.lavarapido.notification.domain.model.TrackedBooking;
import com.lavarapido.notification.domain.port.out.BookingTrackingRepository;
import com.lavarapido.notification.infrastructure.adapter.out.persistence.entity.BookingTrackingJpaEntity;
import com.lavarapido.notification.infrastructure.adapter.out.persistence.repository.BookingTrackingJpaRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

@Component
class BookingTrackingPersistenceAdapter implements BookingTrackingRepository {

    private final BookingTrackingJpaRepository bookings;
    private final Clock clock;

    BookingTrackingPersistenceAdapter(BookingTrackingJpaRepository bookings, Clock clock) {
        this.bookings = bookings;
        this.clock = clock;
    }

    @Override
    public Optional<TrackedBooking> find(long bookingId) {
        return bookings.findById(bookingId).map(BookingTrackingPersistenceAdapter::toDomain);
    }

    @Override
    public List<TrackedBooking> findUnassignedStartingBetween(Instant from, Instant to) {
        return bookings.findByOperatorUserIdIsNullAndCancelledFalseAndUnassignedAlertAtIsNullAndScheduledStartBetween(from, to)
                .stream().map(BookingTrackingPersistenceAdapter::toDomain).toList();
    }

    @Override
    public void markUnassignedAlertSent(long bookingId, Instant at) {
        bookings.findById(bookingId).ifPresent(row -> {
            row.setUnassignedAlertAt(at);
            bookings.save(row);
        });
    }

    private static TrackedBooking toDomain(BookingTrackingJpaEntity row) {
        return new TrackedBooking(row.getBookingId(), row.getBookingCode(), row.getCustomerUserId(),
                row.getOperatorUserId(), row.getScheduledStart(), row.isCancelled());
    }

    /**
     * OPS-12: antes cualquier guardado (por ejemplo, otro BookingConfirmed con la misma hora) borraba la
     * marca del aviso "sin operario" y se volvía a avisar. Ahora se conserva, salvo que cambie la hora
     * (reprogramada) o que la reserva pierda su operario (E-12): ahí sí se puede volver a avisar.
     */
    @Override
    public void save(TrackedBooking booking) {
        Instant alertAt = bookings.findById(booking.bookingId())
                .filter(row -> Objects.equals(row.getScheduledStart(), booking.scheduledStart()))
                .filter(row -> row.getOperatorUserId() == null || booking.operatorUserId() != null)
                .map(BookingTrackingJpaEntity::getUnassignedAlertAt)
                .orElse(null);
        BookingTrackingJpaEntity row = new BookingTrackingJpaEntity(booking.bookingId(), booking.bookingCode(),
                booking.customerUserId(), booking.operatorUserId(), booking.scheduledStart(), booking.cancelled(),
                clock.instant());
        row.setUnassignedAlertAt(alertAt);
        bookings.save(row);
    }
}
