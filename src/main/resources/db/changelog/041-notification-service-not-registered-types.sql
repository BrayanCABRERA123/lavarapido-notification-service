--liquibase formatted sql
-- Aviso cuando un servicio terminó y nadie lo registró (no se inició o no se finalizó): al operario,
-- por si lo atendió y olvidó registrarlo, y a los administradores, que lo cierran desde "Por cerrar".
-- operations-service manda el evento una sola vez por reserva.

--changeset lavarapido:notification-041-seed-service-not-registered-types
--comment: Tipos OPERATOR_SERVICE_NOT_REGISTERED y ADMIN_SERVICE_NOT_REGISTERED en notification_type
MERGE [notification].notification_type AS target
USING (VALUES
    (N'OPERATOR_SERVICE_NOT_REGISTERED', N'Servicio sin registrar (operario)', 25),
    (N'ADMIN_SERVICE_NOT_REGISTERED', N'Servicio sin registrar (administrador)', 26)
) AS source (code, name, display_order)
ON target.code = source.code
WHEN NOT MATCHED THEN
    INSERT (code, name, display_order) VALUES (source.code, source.name, source.display_order);
--rollback DELETE FROM [notification].notification_type WHERE code IN (N'OPERATOR_SERVICE_NOT_REGISTERED', N'ADMIN_SERVICE_NOT_REGISTERED');
