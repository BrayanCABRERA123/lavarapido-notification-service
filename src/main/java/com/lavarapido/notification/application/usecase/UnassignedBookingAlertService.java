package com.lavarapido.notification.application.usecase;

import com.lavarapido.notification.domain.model.NotificationTypeCode;
import com.lavarapido.notification.domain.model.TrackedBooking;
import com.lavarapido.notification.domain.port.in.SendNotificationCommand;
import com.lavarapido.notification.domain.port.in.SendNotificationUseCase;
import com.lavarapido.notification.domain.port.in.UnassignedBookingAlertUseCase;
import com.lavarapido.notification.domain.port.out.BookingTrackingRepository;
import com.lavarapido.notification.domain.port.out.UserContactDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Reserva confirmada que empieza en menos de 2 horas (app.reminders.unassigned-lead), o que ya debía
 * haber empezado hace menos de una hora, y sigue sin operario: se avisa a todos los administradores activos, por bandeja y push (los avisos del
 * personal no van al correo). La foto de cada reserva sale de booking_tracking, que se llena con
 * los eventos; no se consulta booking ni operations.
 *
 * Cada reserva se avisa una sola vez. Si la reprograman, la marca se borra y se vuelve a avisar
 * antes de la hora nueva. Sin administradores (o con security-service caído) no se marca nada y se
 * reintenta en la siguiente vuelta.
 */
@Service
public class UnassignedBookingAlertService implements UnassignedBookingAlertUseCase {

    private static final Logger log = LoggerFactory.getLogger(UnassignedBookingAlertService.class);
    private static final ZoneId COLOMBIA = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm");
    // también se avisa la que ya empezó sin operario, pero no las viejas (para no llenar la bandeja)
    private static final Duration ALREADY_STARTED = Duration.ofHours(1);

    private final BookingTrackingRepository tracking;
    private final UserContactDirectory contacts;
    private final SendNotificationUseCase sender;
    private final Clock clock;
    private final Duration lead;

    public UnassignedBookingAlertService(BookingTrackingRepository tracking, UserContactDirectory contacts,
                                         SendNotificationUseCase sender, Clock clock,
                                         @Value("${app.reminders.unassigned-lead:2h}") Duration lead) {
        this.tracking = tracking;
        this.contacts = contacts;
        this.sender = sender;
        this.clock = clock;
        this.lead = lead;
    }

    @Override
    @Transactional
    public int alertDue() {
        Instant now = clock.instant();
        List<TrackedBooking> due = tracking.findUnassignedStartingBetween(now.minus(ALREADY_STARTED), now.plus(lead));
        if (due.isEmpty()) {
            return 0;
        }
        List<Long> admins = contacts.activeAdminIds();
        if (admins.isEmpty()) {
            log.warn("{} booking(s) without operator, but no active administrator could be read", due.size());
            return 0;
        }
        int sent = 0;
        for (TrackedBooking booking : due) {
            String code = booking.bookingCode() == null ? "#" + booking.bookingId() : booking.bookingCode();
            String when = DATE_TIME.format(booking.scheduledStart().atZone(COLOMBIA));
            List<SendNotificationCommand> commands = admins.stream()
                    .map(admin -> new SendNotificationCommand(admin, NotificationTypeCode.ADMIN_BOOKING_UNASSIGNED,
                            "Reserva sin operario",
                            "La reserva " + code + (booking.scheduledStart().isAfter(now) ? " es el " : " ya debía empezar: era el ")
                                    + when + " y todavía no tiene operario asignado. Asígnalo desde Reservas.",
                            "booking", booking.bookingId()))
                    .toList();
            sender.sendAll(commands);
            tracking.markUnassignedAlertSent(booking.bookingId(), now);
            sent += commands.size();
        }
        log.info("Alerted {} administrator notice(s) about {} booking(s) without operator", sent, due.size());
        return sent;
    }
}
