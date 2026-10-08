package com.lavarapido.notification.domain.service;

import com.lavarapido.notification.domain.model.DomainEventEnvelope;
import com.lavarapido.notification.domain.model.NotificationTypeCode;
import com.lavarapido.notification.domain.port.in.SendNotificationCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** RF-018: qué notificaciones genera cada evento (nombre de la prueba en traceability-matrix.md). */
@DisplayName("EventNotificationFactory (RF-018)")
class NotificationFactoryTest {

    private final EventNotificationFactory factory = new EventNotificationFactory();

    private static DomainEventEnvelope event(String type, Map<String, Object> payload) {
        return new DomainEventEnvelope("evt-1", type, "agg-1", Instant.parse("2026-09-30T15:00:00Z"), 1, payload);
    }

    @Test
    @DisplayName("UserRegistered da la bienvenida al usuario nuevo")
    void welcomesNewUser() {
        List<SendNotificationCommand> commands = factory.from(event("UserRegistered", Map.of("userId", 12)));

        assertEquals(1, commands.size());
        assertEquals(12, commands.getFirst().userId());
        assertEquals(NotificationTypeCode.USER_WELCOME, commands.getFirst().type());
        assertNull(commands.getFirst().referenceEntity());
    }

    @Test
    @DisplayName("la bienvenida lleva el correo y el nombre del evento; un correo inválido se descarta")
    void welcomeCarriesEmail() {
        SendNotificationCommand command = factory.from(event("UserRegistered",
                Map.of("userId", 12, "email", "ana@gmail.com", "firstName", "Ana"))).getFirst();
        assertEquals("ana@gmail.com", command.email());
        assertEquals("Ana", command.recipientName());
        assertTrue(command.hasEmail());

        SendNotificationCommand invalid = factory.from(event("UserRegistered",
                Map.of("userId", 12, "email", "no es un correo"))).getFirst();
        assertFalse(invalid.hasEmail());
    }

    @Test
    @DisplayName("BookingConfirmed avisa al cliente con la fecha en hora de Colombia y enlace a la reserva")
    void confirmsBookingToCustomer() {
        List<SendNotificationCommand> commands = factory.from(event("BookingConfirmed", Map.of(
                "bookingId", 145, "bookingCode", "RES-8910", "customerUserId", "7",
                "scheduledStart", "2026-10-01T15:00:00Z")));

        assertEquals(1, commands.size());
        SendNotificationCommand command = commands.getFirst();
        assertEquals(7, command.userId());
        assertEquals(NotificationTypeCode.BOOKING_CONFIRMED, command.type());
        assertEquals("booking", command.referenceEntity());
        assertEquals(145L, command.referenceId());
        // 15:00 UTC = 10:00 en Bogotá
        assertTrue(command.message().contains("01/10/2026 a las 10:00"), command.message());
        assertTrue(command.message().contains("RES-8910"), command.message());
    }

    @Test
    @DisplayName("BookingConfirmed con operario avisa también al operario")
    void confirmsBookingToOperatorToo() {
        List<SendNotificationCommand> commands = factory.from(event("BookingConfirmed", Map.of(
                "bookingId", 145, "customerUserId", 7, "operatorUserId", 3)));

        assertEquals(2, commands.size());
        assertEquals(3, commands.get(1).userId());
        assertEquals(NotificationTypeCode.OPERATOR_ASSIGNED, commands.get(1).type());
    }

    @Test
    @DisplayName("PaymentRejected incluye el monto en pesos y el motivo")
    void rejectsPaymentWithReason() {
        List<SendNotificationCommand> commands = factory.from(event("PaymentRejected", Map.of(
                "paymentId", 90, "customerUserId", 7, "amount", 45000, "reason", "El monto no coincide")));

        SendNotificationCommand command = commands.getFirst();
        assertEquals(NotificationTypeCode.PAYMENT_REJECTED, command.type());
        assertEquals("payment", command.referenceEntity());
        assertTrue(command.message().contains("$45.000"), command.message());
        assertTrue(command.message().contains("El monto no coincide"), command.message());
    }

    @Test
    @DisplayName("quien se registra solo recibe la bienvenida de cliente, sin la nota de la contraseña")
    void selfRegisteredClientWelcome() {
        String message = factory.from(event("UserRegistered", Map.of("userId", 12, "roles", List.of("CLIENT"),
                "createdByAdmin", false))).getFirst().message();

        assertTrue(message.contains("reservar tu primer lavado"), message);
        assertFalse(message.contains("contraseña"), message);
    }

    @Test
    @DisplayName("un operario creado por el admin recibe la bienvenida de operario y cómo entrar")
    void operatorCreatedByAdminWelcome() {
        String message = factory.from(event("UserRegistered", Map.of("userId", 12, "roles", List.of("OPERATOR"),
                "createdByAdmin", true))).getFirst().message();

        assertTrue(message.contains("como operario"), message);
        assertFalse(message.contains("vehículos"), message);
        assertTrue(message.contains("Un administrador creó tu cuenta"), message);
        assertTrue(message.contains("¿Olvidaste tu contraseña?"), message);
    }

    @Test
    @DisplayName("si la cuenta tiene varios roles, la bienvenida es la del de más privilegios")
    void adminWinsOverOtherRoles() {
        String message = factory.from(event("UserRegistered", Map.of("userId", 12,
                "roles", List.of("CLIENT", "ADMIN"), "createdByAdmin", true))).getFirst().message();

        assertTrue(message.contains("cuenta de administrador"), message);
    }

    @Test
    @DisplayName("un evento viejo sin roles ni createdByAdmin sigue dando la bienvenida de cliente")
    void oldEventWithoutTheNewFields() {
        String message = factory.from(event("UserRegistered", Map.of("userId", 12))).getFirst().message();

        assertTrue(message.contains("reservar tu primer lavado"), message);
        assertFalse(message.contains("contraseña"), message);
    }

    @Test
    @DisplayName("PromotionRedeemed confirma el cupón con el nombre, la reserva y el descuento")
    void confirmsRedeemedCoupon() {
        // mismo payload que publica payment-service (LoyaltyApplicationService.RedeemAsync)
        List<SendNotificationCommand> commands = factory.from(event("PromotionRedeemed", Map.of(
                "customerUserId", 7, "bookingId", 145, "bookingCode", "RES-000145",
                "promotionCode", "LAVA20", "promotionName", "Lavado 20%", "discountAmount", 12500.0)));

        assertEquals(1, commands.size());
        SendNotificationCommand command = commands.getFirst();
        assertEquals(7, command.userId());
        assertEquals(NotificationTypeCode.PROMOTION_REDEEMED, command.type());
        assertEquals("booking", command.referenceEntity());
        assertEquals(145L, command.referenceId());
        assertEquals("Cupón canjeado: Lavado 20%", command.title());
        assertTrue(command.message().contains("RES-000145"), command.message());
        assertTrue(command.message().contains("$12.500"), command.message());
    }

    @Test
    @DisplayName("PromotionRedeemed sin nombre ni monto sigue armando un mensaje legible")
    void redeemedCouponWithoutOptionalData() {
        SendNotificationCommand command = factory.from(event("PromotionRedeemed",
                Map.of("customerUserId", 7))).getFirst();

        assertEquals("Cupón canjeado: tu promoción", command.title());
        assertFalse(command.message().contains("descontamos"), command.message());
    }

    @Test
    @DisplayName("sin el user_id del destinatario no se crea notificación")
    void skipsWithoutRecipient() {
        assertTrue(factory.from(event("BookingCreated", Map.of("bookingId", 145))).isEmpty());
        assertTrue(factory.from(event("BookingCreated", Map.of("customerUserId", "abc"))).isEmpty());
    }

    @Test
    @DisplayName("sin id del objeto la notificación va sin enlace")
    void noReferenceWithoutId() {
        SendNotificationCommand command = factory.from(event("ServiceCompleted", Map.of("customerUserId", 7))).getFirst();

        assertNull(command.referenceEntity());
        assertNull(command.referenceId());
    }

    @Test
    @DisplayName("los eventos que no notifican no generan nada")
    void ignoresOtherEvents() {
        assertTrue(factory.from(event("UserAuthenticated", Map.of("userId", 7))).isEmpty());
        assertTrue(factory.from(event("RatingSubmitted", Map.of("customerUserId", 7))).isEmpty());
        assertTrue(factory.from(event(null, Map.of())).isEmpty());
    }
}
