--liquibase formatted sql
-- OPS-05 (E-14): push y correo pendientes o por reintentar. Se escriben en la misma transacción que la
-- notificación y salen después del commit; si fallan, una tarea programada los reintenta hasta un tope.
-- Una fila por notificación y canal: la misma entrega no se envía dos veces.

--changeset lavarapido:notification-047-pending-delivery
--comment: Entregas de push y correo pendientes (con reintentos)
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM sys.tables t JOIN sys.schemas s ON s.schema_id = t.schema_id WHERE s.name = 'notification' AND t.name = 'pending_delivery'
CREATE TABLE [notification].pending_delivery (
    pending_delivery_id BIGINT        IDENTITY(1,1) NOT NULL,
    notification_id     BIGINT        NOT NULL,
    channel             VARCHAR(10)   NOT NULL,
    email_address       NVARCHAR(254) NULL,
    recipient_name      NVARCHAR(100) NULL,
    language            VARCHAR(5)    NULL,
    attempts            INT           NOT NULL CONSTRAINT df_pending_delivery_attempts DEFAULT 0,
    next_attempt_at     DATETIME2(3)  NOT NULL,
    last_error          NVARCHAR(300) NULL,
    delivered_at        DATETIME2(3)  NULL,
    failed_at           DATETIME2(3)  NULL,
    created_at          DATETIME2(3)  NOT NULL CONSTRAINT df_pending_delivery_created DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_pending_delivery PRIMARY KEY (pending_delivery_id),
    CONSTRAINT fk_pending_delivery_notification FOREIGN KEY (notification_id)
        REFERENCES [notification].notification (notification_id),
    CONSTRAINT uq_pending_delivery_channel UNIQUE (notification_id, channel),
    CONSTRAINT ck_pending_delivery_channel CHECK (channel IN ('PUSH', 'EMAIL')),
    CONSTRAINT ck_pending_delivery_attempts CHECK (attempts >= 0)
);
CREATE NONCLUSTERED INDEX ix_pending_delivery_due ON [notification].pending_delivery (next_attempt_at)
    WHERE delivered_at IS NULL AND failed_at IS NULL;
--rollback DROP TABLE [notification].pending_delivery;
