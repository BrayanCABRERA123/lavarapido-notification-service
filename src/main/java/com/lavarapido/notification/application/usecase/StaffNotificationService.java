package com.lavarapido.notification.application.usecase;

import com.lavarapido.notification.domain.model.BookingEventPayload;
import com.lavarapido.notification.domain.model.DomainEventEnvelope;
import com.lavarapido.notification.domain.model.TrackedBooking;
import com.lavarapido.notification.domain.port.in.SendNotificationCommand;
import com.lavarapido.notification.domain.port.in.SendNotificationUseCase;
import com.lavarapido.notification.domain.port.out.BookingTrackingRepository;
import com.lavarapido.notification.domain.port.out.UserContactDirectory;
import com.lavarapido.notification.domain.service.StaffNotificationFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Avisos al operario y al administrador a partir de los eventos de reserva y asignación
 * (StaffNotificationFactory), y la foto de cada reserva que necesitan (booking_tracking).
 *
 * Corre dentro de la transacción de DomainEventConsumerService: si algo falla, el evento se
 * reintenta completo y no quedan avisos a medias. Los ids de los administradores se piden a
 * security-service solo cuando el evento les genera algo.
 */
@Service
public class StaffNotificationService {

    private static final Set<String> TRACKED_EVENTS = Set.of("BookingConfirmed", "BookingCancelled", "OperatorAssigned");
    private static final Set<String> ADMIN_EVENTS = Set.of("BookingConfirmed", "BookingCancelled");

    private final BookingTrackingRepository tracking;
    private final UserContactDirectory contacts;
    private final StaffNotificationFactory factory;
    private final SendNotificationUseCase sender;

    public StaffNotificationService(BookingTrackingRepository tracking, UserContactDirectory contacts,
                                    StaffNotificationFactory factory, SendNotificationUseCase sender) {
        this.tracking = tracking;
        this.contacts = contacts;
        this.factory = factory;
        this.sender = sender;
    }

    /** @return cuántos avisos se crearon para el personal */
    public int handle(DomainEventEnvelope event) {
        if (!TRACKED_EVENTS.contains(event.eventType())) {
            return 0;
        }
        Optional<BookingEventPayload> booking = BookingEventPayload.from(event);
        if (booking.isEmpty()) {
            return 0;
        }
        Optional<TrackedBooking> previous = tracking.find(booking.get().bookingId());
        List<Long> admins = ADMIN_EVENTS.contains(event.eventType()) ? contacts.activeAdminIds() : List.of();

        // sin correo: los avisos del personal son de bandeja y push
        List<SendNotificationCommand> commands = factory.from(event, previous, admins);
        sender.sendAll(commands);
        factory.track(event, previous).ifPresent(tracking::save);
        return commands.size();
    }
}
