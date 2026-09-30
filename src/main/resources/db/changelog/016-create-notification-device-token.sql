--liquibase formatted sql
-- Tabla NUEVA (no está en lavarapido-6-services-sqlserver.sql), decidida en ADR-011:
-- los celulares que reciben notificaciones push. El token lo entrega Expo al instalar la app.
--
-- user_id apunta a security.app_user sin FK (otro servicio), con índice como pide ADR-003.
-- token es único: un celular tiene un solo dueño a la vez (si otra cuenta inicia sesión en él,
-- la fila se reasigna en vez de duplicarse).

--changeset lavarapido:notification-016-device-token
--comment: Celulares registrados para recibir push (ADR-011)
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM sys.tables t JOIN sys.schemas s ON s.schema_id = t.schema_id WHERE s.name = 'notification' AND t.name = 'device_token'
CREATE TABLE [notification].device_token (
    device_token_id BIGINT        IDENTITY(1,1) NOT NULL,
    user_id         BIGINT        NOT NULL,   -- [security].app_user
    token           NVARCHAR(200) NOT NULL,
    platform        NVARCHAR(10)  NOT NULL,
    is_active       BIT           NOT NULL CONSTRAINT df_dtoken_active DEFAULT 1,
    last_seen_at    DATETIME2(3)  NOT NULL CONSTRAINT df_dtoken_seen DEFAULT SYSUTCDATETIME(),
    created_at      DATETIME2(3)  NOT NULL CONSTRAINT df_dtoken_created DEFAULT SYSUTCDATETIME(),
    created_by      BIGINT        NULL,
    updated_at      DATETIME2(3)  NULL,
    updated_by      BIGINT        NULL,
    deleted_at      DATETIME2(3)  NULL,
    deleted_by      BIGINT        NULL,
    row_version     INT           NOT NULL CONSTRAINT df_dtoken_rv DEFAULT 1,
    CONSTRAINT pk_device_token PRIMARY KEY (device_token_id),
    CONSTRAINT uq_device_token_token UNIQUE (token),
    CONSTRAINT ck_device_token_platform CHECK (platform IN (N'ANDROID', N'IOS'))
);

--changeset lavarapido:notification-016-ix-device-user
--comment: Buscar los celulares activos de un usuario al mandar una push
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM sys.indexes WHERE name = 'ix_device_token_user_active'
CREATE NONCLUSTERED INDEX ix_device_token_user_active ON [notification].device_token (user_id, is_active);
