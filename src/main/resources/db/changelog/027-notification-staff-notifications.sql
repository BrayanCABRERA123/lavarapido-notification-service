--liquibase formatted sql
-- Avisos para el personal (RF-018): el operario se entera si su servicio se cancela, cambia de
-- hora o se le asigna a otro; el administrador se entera de reservas nuevas, reprogramadas y
-- canceladas (con el motivo).
--
-- notification-service no consulta booking ni operations: guarda aquí lo último que supo de cada
-- reserva por los eventos (BookingConfirmed, BookingCancelled, OperatorAssigned). Así sabe si un
-- BookingConfirmed es una reserva nueva o una reprogramada, y a qué operario avisarle.

--changeset lavarapido:notification-027-booking-tracking
--comment: Última foto de cada reserva según los eventos (para avisar al personal)
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM sys.tables t JOIN sys.schemas s ON s.schema_id = t.schema_id WHERE s.name = 'notification' AND t.name = 'booking_tracking'
CREATE TABLE [notification].booking_tracking (
    booking_id        BIGINT       NOT NULL,   -- booking.booking, sin FK: es de otro servicio
    booking_code      NVARCHAR(20) NULL,
    customer_user_id  BIGINT       NULL,       -- security.app_user (el cliente)
    operator_user_id  BIGINT       NULL,       -- security.app_user del operario asignado
    scheduled_start   DATETIME2(3) NULL,
    is_cancelled      BIT          NOT NULL CONSTRAINT df_btracking_cancelled DEFAULT 0,
    updated_at        DATETIME2(3) NOT NULL CONSTRAINT df_btracking_updated DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_booking_tracking PRIMARY KEY (booking_id)
);
--rollback DROP TABLE [notification].booking_tracking;

--changeset lavarapido:notification-027-seed-staff-types
--comment: Tipos de los avisos para el operario y el administrador
MERGE [notification].notification_type AS target
USING (VALUES
    (N'OPERATOR_SERVICE_CANCELLED',   N'Servicio cancelado (operario)',      14),
    (N'OPERATOR_SERVICE_RESCHEDULED', N'Servicio reprogramado (operario)',   15),
    (N'OPERATOR_SERVICE_UNASSIGNED',  N'Servicio reasignado (operario)',     16),
    (N'ADMIN_BOOKING_CREATED',        N'Reserva nueva (administrador)',      17),
    (N'ADMIN_BOOKING_RESCHEDULED',    N'Reserva reprogramada (administrador)', 18),
    (N'ADMIN_BOOKING_CANCELLED',      N'Reserva cancelada (administrador)',  19)
) AS source (code, name, display_order)
ON target.code = source.code
WHEN NOT MATCHED THEN
    INSERT (code, name, display_order) VALUES (source.code, source.name, source.display_order);
