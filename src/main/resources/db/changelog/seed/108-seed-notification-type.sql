--liquibase formatted sql
-- Catálogo de tipos de notificación (06-data/migration-strategy.md: seed 108).
-- El code es el mismo nombre del enum NotificationTypeCode en Java. Cada fila se inserta solo si
-- no existe, así el seed se puede correr sobre una base donde el script completo ya lo cargó.

--changeset lavarapido:notification-108-seed-types
--comment: Los tipos de evento que generan notificación
MERGE [notification].notification_type AS target
USING (VALUES
    (N'USER_WELCOME',        N'Bienvenida',                1),
    (N'BOOKING_CREATED',     N'Reserva registrada',        2),
    (N'BOOKING_CONFIRMED',   N'Reserva confirmada',        3),
    (N'BOOKING_CANCELLED',   N'Reserva cancelada',         4),
    (N'BOOKING_REMINDER',    N'Recordatorio de reserva',   5),
    (N'OPERATOR_ASSIGNED',   N'Operario asignado',         6),
    (N'SERVICE_STARTED',     N'Servicio iniciado',         7),
    (N'SERVICE_COMPLETED',   N'Servicio finalizado',       8),
    (N'PAYMENT_CONFIRMED',   N'Pago aprobado',             9),
    (N'PAYMENT_REJECTED',    N'Pago rechazado',           10),
    (N'PROMOTION_AVAILABLE', N'Promoción disponible',     11),
    (N'SYSTEM_MESSAGE',      N'Mensaje del administrador', 12)
) AS source (code, name, display_order)
ON target.code = source.code
WHEN NOT MATCHED THEN
    INSERT (code, name, display_order) VALUES (source.code, source.name, source.display_order);
