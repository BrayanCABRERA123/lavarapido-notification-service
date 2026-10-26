package com.lavarapido.notification.infrastructure.adapter.out.persistence;

import com.lavarapido.notification.domain.model.TrackedBooking;
import com.lavarapido.notification.infrastructure.adapter.out.persistence.entity.BookingTrackingJpaEntity;
import com.lavarapido.notification.infrastructure.adapter.out.persistence.repository.BookingTrackingJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("BookingTrackingPersistenceAdapter (lo último que se supo de cada reserva)")
class BookingTrackingPersistenceAdapterTest {

    private static final Instant START = Instant.parse("2026-10-10T14:00:00Z");
    private static final Instant ALERTED = Instant.parse("2026-10-10T12:00:00Z");

    private final BookingTrackingJpaRepository rows = mock(BookingTrackingJpaRepository.class);
    private final BookingTrackingPersistenceAdapter adapter = new BookingTrackingPersistenceAdapter(rows,
            Clock.fixed(Instant.parse("2026-10-10T12:30:00Z"), ZoneOffset.UTC));

    private void storedAlerted(Long operator) {
        BookingTrackingJpaEntity row = new BookingTrackingJpaEntity(145L, "RES-000145", 7L, operator, START, false, ALERTED);
        row.setUnassignedAlertAt(ALERTED);
        given(rows.findById(145L)).willReturn(Optional.of(row));
    }

    private BookingTrackingJpaEntity saved() {
        ArgumentCaptor<BookingTrackingJpaEntity> captor = ArgumentCaptor.forClass(BookingTrackingJpaEntity.class);
        verify(rows).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("OPS-12: otro BookingConfirmed con la misma hora no borra la marca del aviso (no se repite)")
    void sameTimeKeepsTheAlertMark() {
        storedAlerted(null);

        adapter.save(new TrackedBooking(145L, "RES-000145", 7L, null, START, false));

        assertEquals(ALERTED, saved().getUnassignedAlertAt());
    }

    @Test
    @DisplayName("OPS-12: si cambia la hora, la marca se borra y se vuelve a avisar antes de la hora nueva")
    void rescheduleClearsTheAlertMark() {
        storedAlerted(null);

        adapter.save(new TrackedBooking(145L, "RES-000145", 7L, null, START.plusSeconds(86_400), false));

        assertNull(saved().getUnassignedAlertAt());
    }

    @Test
    @DisplayName("si la reserva pierde su operario (E-12), se puede volver a avisar")
    void losingTheOperatorClearsTheAlertMark() {
        storedAlerted(30L);

        adapter.save(new TrackedBooking(145L, "RES-000145", 7L, null, START, false));

        assertNull(saved().getUnassignedAlertAt());
    }
}
