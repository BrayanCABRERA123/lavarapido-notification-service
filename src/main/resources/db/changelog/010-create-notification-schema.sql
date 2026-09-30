--liquibase formatted sql
-- Las 2 tablas del notification-service: notification_type y notification.
-- Mismo DDL de lavarapido-6-services-sqlserver.sql, sección 6 (06-data/models.md, 09 notification).
-- Cada changeset tiene precondición: si el script completo ya creó el objeto, se marca como
-- ejecutado en vez de fallar.

--changeset lavarapido:notification-010-schema
--comment: Crea el esquema notification, que es de este servicio
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM sys.schemas WHERE name = 'notification'
CREATE SCHEMA [notification];

--changeset lavarapido:notification-010-notification-type
--comment: Catálogo de los eventos que generan una notificación
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM sys.tables t JOIN sys.schemas s ON s.schema_id = t.schema_id WHERE s.name = 'notification' AND t.name = 'notification_type'
CREATE TABLE [notification].notification_type (
    notification_type_id SMALLINT     IDENTITY(1,1) NOT NULL,
    code          NVARCHAR(40) NOT NULL,
    name          NVARCHAR(80) NOT NULL,
    display_order SMALLINT     NOT NULL CONSTRAINT df_ntype_order DEFAULT 0,
    is_active     BIT          NOT NULL CONSTRAINT df_ntype_active DEFAULT 1,
    created_at    DATETIME2(3) NOT NULL CONSTRAINT df_ntype_created DEFAULT SYSUTCDATETIME(),
    created_by    BIGINT       NULL,
    updated_at    DATETIME2(3) NULL,
    updated_by    BIGINT       NULL,
    deleted_at    DATETIME2(3) NULL,
    deleted_by    BIGINT       NULL,
    row_version   INT          NOT NULL CONSTRAINT df_ntype_rv DEFAULT 1,
    CONSTRAINT pk_notification_type PRIMARY KEY (notification_type_id),
    CONSTRAINT uq_notification_type_code UNIQUE (code)
);

--changeset lavarapido:notification-010-notification
--comment: Notificación enviada a un usuario. user_id apunta a security.app_user sin FK (otro servicio)
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM sys.tables t JOIN sys.schemas s ON s.schema_id = t.schema_id WHERE s.name = 'notification' AND t.name = 'notification'
CREATE TABLE [notification].notification (
    notification_id      BIGINT        IDENTITY(1,1) NOT NULL,
    user_id              BIGINT        NOT NULL,   -- [security].app_user
    notification_type_id SMALLINT      NOT NULL,
    title                NVARCHAR(120) NOT NULL,
    [message]            NVARCHAR(500) NOT NULL,
    reference_entity     NVARCHAR(40)  NULL,
    reference_id         BIGINT        NULL,
    is_read              BIT           NOT NULL CONSTRAINT df_notif_read DEFAULT 0,
    read_at              DATETIME2(3)  NULL,
    sent_at              DATETIME2(3)  NOT NULL CONSTRAINT df_notif_sent DEFAULT SYSUTCDATETIME(),
    created_at           DATETIME2(3)  NOT NULL CONSTRAINT df_notif_created DEFAULT SYSUTCDATETIME(),
    created_by           BIGINT        NULL,
    updated_at           DATETIME2(3)  NULL,
    updated_by           BIGINT        NULL,
    deleted_at           DATETIME2(3)  NULL,
    deleted_by           BIGINT        NULL,
    row_version          INT           NOT NULL CONSTRAINT df_notif_rv DEFAULT 1,
    CONSTRAINT pk_notification PRIMARY KEY (notification_id),
    CONSTRAINT fk_notification_type FOREIGN KEY (notification_type_id) REFERENCES [notification].notification_type(notification_type_id),
    CONSTRAINT ck_notification_read CHECK (
        (is_read = 0 AND read_at IS NULL) OR
        (is_read = 1 AND read_at IS NOT NULL)
    )
);

--changeset lavarapido:notification-010-ix-user-unread
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM sys.indexes WHERE name = 'ix_notification_user_unread'
CREATE NONCLUSTERED INDEX ix_notification_user_unread ON [notification].notification (user_id, is_read);

--changeset lavarapido:notification-010-ix-user-date
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM sys.indexes WHERE name = 'ix_notification_user_date'
CREATE NONCLUSTERED INDEX ix_notification_user_date ON [notification].notification (user_id, sent_at);
