--liquibase formatted sql
-- Aviso al administrador cuando una reserva confirmada empieza en menos de 2 horas y todavía no tiene
-- operario asignado. booking_tracking ya sabe el operario de cada reserva (migración 027); esta
-- columna guarda cuándo se avisó para no repetir el aviso en cada vuelta de la tarea programada.

--changeset lavarapido:notification-031-booking-tracking-unassigned-alert
--comment: Cuándo se avisó a los administradores que la reserva seguía sin operario
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM sys.columns WHERE object_id = OBJECT_ID('notification.booking_tracking') AND name = 'unassigned_alert_at'
ALTER TABLE [notification].booking_tracking ADD unassigned_alert_at DATETIME2(3) NULL;
--rollback ALTER TABLE [notification].booking_tracking DROP COLUMN unassigned_alert_at;

--changeset lavarapido:notification-031-seed-unassigned-booking-type
--comment: Tipo ADMIN_BOOKING_UNASSIGNED en el catálogo notification_type
MERGE [notification].notification_type AS target
USING (VALUES
    (N'ADMIN_BOOKING_UNASSIGNED', N'Reserva sin operario (administrador)', 23)
) AS source (code, name, display_order)
ON target.code = source.code
WHEN NOT MATCHED THEN
    INSERT (code, name, display_order) VALUES (source.code, source.name, source.display_order);
