package com.lavarapido.notification.infrastructure.adapter.in.web;

import com.lavarapido.notification.application.fake.Fakes.InMemoryContacts;
import com.lavarapido.notification.application.fake.Fakes.InMemoryDevices;
import com.lavarapido.notification.application.fake.Fakes.InMemoryNotifications;
import com.lavarapido.notification.application.fake.Fakes.RecordingEmailSender;
import com.lavarapido.notification.application.fake.Fakes.RecordingPushSender;
import com.lavarapido.notification.application.usecase.EmailRecipients;
import com.lavarapido.notification.application.usecase.NotificationSendingService;
import com.lavarapido.notification.domain.exception.NotificationTypeNotAllowedException;
import com.lavarapido.notification.domain.model.NotificationFilter;
import com.lavarapido.notification.domain.model.NotificationTypeCode;
import com.lavarapido.notification.application.usecase.NotificationInboxService;
import com.lavarapido.notification.infrastructure.adapter.in.web.dto.AdminNotificationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Mensajes del administrador")
class AdminNotificationControllerTest {

    private static final long ANA = 7;

    private InMemoryNotifications notifications;
    private RecordingEmailSender email;
    private AdminNotificationController controller;

    @BeforeEach
    void setUp() {
        notifications = new InMemoryNotifications();
        email = new RecordingEmailSender();
        InMemoryContacts contacts = new InMemoryContacts().with(ANA, "ana@gmail.com", "Ana", true);
        NotificationSendingService sender = new NotificationSendingService(notifications, new InMemoryDevices(),
                new RecordingPushSender(), email, contacts, Clock.systemUTC());
        controller = new AdminNotificationController(sender, new EmailRecipients(contacts));
    }

    @Test
    @DisplayName("el aviso de inspección llega a la bandeja del cliente y a su correo")
    void inspectionReportIsEmailed() {
        Map<String, Integer> result = controller.send(new AdminNotificationRequest(List.of(ANA), "INSPECTION_REPORT",
                "Inspección de tu vehículo", "Terminamos la inspección de tu reserva RES-000145: 2 hallazgos."));

        assertEquals(1, result.get("sent"));
        assertEquals(NotificationTypeCode.INSPECTION_REPORT, new NotificationInboxService(notifications, Clock.systemUTC())
                .list(ANA, NotificationFilter.none(), 0, 20).items().getFirst().type());
        assertEquals(List.of("ana@gmail.com"), email.sentTo);
    }

    @Test
    @DisplayName("un mensaje del sistema (sin tipo) se queda en la bandeja, sin correo")
    void systemMessageIsNotEmailed() {
        controller.send(new AdminNotificationRequest(List.of(ANA), null, "Aviso", "Hoy cerramos a las 5 p. m."));

        assertEquals(1, notifications.size());
        assertTrue(email.sentTo.isEmpty());
    }

    @Test
    @DisplayName("un tipo que solo generan los eventos no se puede enviar a mano (ni bandeja ni correo)")
    void eventTypesCannotBeSentByHand() {
        AdminNotificationRequest fakePayment = new AdminNotificationRequest(List.of(ANA), "PAYMENT_CONFIRMED",
                "Pago aprobado", "Aprobamos tu pago de $20.000.");

        NotificationTypeNotAllowedException error =
                assertThrows(NotificationTypeNotAllowedException.class, () -> controller.send(fakePayment));

        assertEquals("NOTIFICATION_TYPE_NOT_ALLOWED", error.code());
        assertEquals(0, notifications.size());
        assertTrue(email.sentTo.isEmpty());
    }
}
