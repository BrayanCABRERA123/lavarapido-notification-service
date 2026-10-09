--liquibase formatted sql
-- Nuevos tipos de notificación:
-- - LOYALTY_POINTS_EARNED: puntos que ganó el cliente con una reserva pagada (payment-service los
--   publica como payment.loyalty_points_earned). Los cupones que esos puntos desbloquean llegan
--   como PROMOTION_AVAILABLE, que ya estaba en el catálogo (seed 108).
-- - INSPECTION_REPORT: el administrador le avisa al cliente que terminó la inspección de su
--   vehículo (reemplaza el envío por WhatsApp, que no hace parte del sistema).

--changeset lavarapido:notification-029-seed-loyalty-and-inspection-types
--comment: Tipos LOYALTY_POINTS_EARNED e INSPECTION_REPORT en el catálogo notification_type
MERGE [notification].notification_type AS target
USING (VALUES
    (N'LOYALTY_POINTS_EARNED', N'Puntos ganados',          21),
    (N'INSPECTION_REPORT',     N'Inspección del vehículo', 22)
) AS source (code, name, display_order)
ON target.code = source.code
WHEN NOT MATCHED THEN
    INSERT (code, name, display_order) VALUES (source.code, source.name, source.display_order);
