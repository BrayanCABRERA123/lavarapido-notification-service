--liquibase formatted sql
-- A-11: aviso al cliente cuando el administrador marca su reserva como "No asistió". Antes salía con el
-- tipo de reserva cancelada; ahora tiene el suyo (misma categoría CANCELLATION en las pantallas).

--changeset lavarapido:notification-116-seed-booking-no-show-type
--comment: Tipo BOOKING_NO_SHOW en notification_type
MERGE [notification].notification_type AS target
USING (VALUES
    (N'BOOKING_NO_SHOW', N'Reserva no asistida', 27)
) AS source (code, name, display_order)
ON target.code = source.code
WHEN NOT MATCHED THEN
    INSERT (code, name, display_order) VALUES (source.code, source.name, source.display_order);
--rollback DELETE FROM [notification].notification_type WHERE code = N'BOOKING_NO_SHOW';
