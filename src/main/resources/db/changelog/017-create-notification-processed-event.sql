--liquibase formatted sql
-- Tabla NUEVA (no está en lavarapido-6-services-sqlserver.sql), decidida en ADR-011.
-- cross-cutting.md §7 pide que cada consumidor revise el eventId "contra su propio registro de
-- eventos procesados" en la misma transacción del trabajo: esta es esa tabla. RabbitMQ entrega
-- al menos una vez, así que sin ella un evento repetido crearía la notificación dos veces.

--changeset lavarapido:notification-017-processed-event
--comment: Eventos de dominio ya procesados, para no crear notificaciones duplicadas
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM sys.tables t JOIN sys.schemas s ON s.schema_id = t.schema_id WHERE s.name = 'notification' AND t.name = 'processed_event'
CREATE TABLE [notification].processed_event (
    processed_event_id BIGINT       IDENTITY(1,1) NOT NULL,
    event_id           NVARCHAR(64) NOT NULL,
    event_type         NVARCHAR(60) NOT NULL,
    processed_at       DATETIME2(3) NOT NULL CONSTRAINT df_pevent_processed DEFAULT SYSUTCDATETIME(),
    created_at         DATETIME2(3) NOT NULL CONSTRAINT df_pevent_created DEFAULT SYSUTCDATETIME(),
    created_by         BIGINT       NULL,
    updated_at         DATETIME2(3) NULL,
    updated_by         BIGINT       NULL,
    deleted_at         DATETIME2(3) NULL,
    deleted_by         BIGINT       NULL,
    row_version        INT          NOT NULL CONSTRAINT df_pevent_rv DEFAULT 1,
    CONSTRAINT pk_processed_event PRIMARY KEY (processed_event_id),
    CONSTRAINT uq_processed_event_event UNIQUE (event_id)
);
