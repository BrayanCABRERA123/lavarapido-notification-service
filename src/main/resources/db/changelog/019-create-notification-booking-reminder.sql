--liquibase formatted sql
-- Tabla NUEVA (no está en lavarapido-6-services-sqlserver.sql): recordatorios de reserva
-- programados, decidida como ampliación de ADR-011.
--
-- notification-service no consulta las reservas de booking-service: cuando llega
-- BookingConfirmed guarda aquí cuándo avisar (por defecto 24 h y 1 h antes) y una tarea
-- programada los envía. BookingCancelled los cancela; una reserva reprogramada vuelve a llegar
-- como BookingConfirmed y mueve sus recordatorios a la nueva hora.

--changeset lavarapido:notification-019-booking-reminder
--comment: Recordatorios de reserva por enviar (uno por reserva y anticipación)
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM sys.tables t JOIN sys.schemas s ON s.schema_id = t.schema_id WHERE s.name = 'notification' AND t.name = 'booking_reminder'
CREATE TABLE [notification].booking_reminder (
    booking_reminder_id BIGINT       IDENTITY(1,1) NOT NULL,
    booking_id          BIGINT       NOT NULL,   -- booking.booking, sin FK: es de otro servicio
    user_id             BIGINT       NOT NULL,   -- security.app_user (el cliente)
    booking_code        NVARCHAR(20) NULL,
    scheduled_start     DATETIME2(3) NOT NULL,
    lead_minutes        INT          NOT NULL,   -- cuántos minutos antes de la cita se avisa
    remind_at           DATETIME2(3) NOT NULL,
    sent_at             DATETIME2(3) NULL,
    created_at          DATETIME2(3) NOT NULL CONSTRAINT df_breminder_created DEFAULT SYSUTCDATETIME(),
    created_by          BIGINT       NULL,
    updated_at          DATETIME2(3) NULL,
    updated_by          BIGINT       NULL,
    deleted_at          DATETIME2(3) NULL,       -- reserva cancelada: el recordatorio ya no se envía
    deleted_by          BIGINT       NULL,
    row_version         INT          NOT NULL CONSTRAINT df_breminder_rv DEFAULT 1,
    CONSTRAINT pk_booking_reminder PRIMARY KEY (booking_reminder_id),
    CONSTRAINT uq_booking_reminder_lead UNIQUE (booking_id, lead_minutes),
    CONSTRAINT ck_booking_reminder_lead CHECK (lead_minutes > 0)
);
CREATE NONCLUSTERED INDEX ix_booking_reminder_due ON [notification].booking_reminder (remind_at)
    WHERE sent_at IS NULL AND deleted_at IS NULL;
