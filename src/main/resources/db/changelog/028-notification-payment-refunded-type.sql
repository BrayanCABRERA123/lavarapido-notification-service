--liquibase formatted sql
-- Nuevo tipo de notificación: el administrador reembolsó un pago aprobado. payment-service lo
-- publica como payment.refunded (PaymentIntegrationEvents) con paymentId, bookingId,
-- customerUserId y amount; el cliente lo recibe en la bandeja y por correo.

--changeset lavarapido:notification-028-seed-payment-refunded-type
--comment: Tipo PAYMENT_REFUNDED en el catálogo notification_type
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM [notification].notification_type WHERE code = N'PAYMENT_REFUNDED'
INSERT INTO [notification].notification_type (code, name, display_order)
VALUES (N'PAYMENT_REFUNDED', N'Pago reembolsado', 20);
