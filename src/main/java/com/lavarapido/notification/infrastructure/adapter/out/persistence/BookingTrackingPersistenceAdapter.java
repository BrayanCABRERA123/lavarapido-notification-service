package com.lavarapido.notification.infrastructure.adapter.out.persistence;

import com.lavarapido.notification.domain.model.TrackedBooking;
import com.lavarapido.notification.domain.port.out.BookingTrackingRepository;
import com.lavarapido.notification.infrastructure.adapter.out.persistence.entity.BookingTrackingJpaEntity;
import com.lavarapido.notification.infrastructure.adapter.out.persistence.repository.BookingTrackingJpaRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
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
        return bookings.findById(bookingId).map(row -> new TrackedBooking(row.getBookingId(), row.getBookingCode(),
                row.getCustomerUserId(), row.getOperatorUserId(), row.getScheduledStart(), row.isCancelled()));
    }

    @Override
    public void save(TrackedBooking booking) {
        bookings.save(new BookingTrackingJpaEntity(booking.bookingId(), booking.bookingCode(), booking.customerUserId(),
                booking.operatorUserId(), booking.scheduledStart(), booking.cancelled(), clock.instant()));
    }
}
