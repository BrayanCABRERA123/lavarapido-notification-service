--liquibase formatted sql
-- Hibernate lee todo el texto como caracteres nacionales (use_nationalized_character_data), y el driver
-- de SQL Server no convierte VARCHAR a NCHAR al leer: channel y language de pending_delivery pasan a
-- NVARCHAR, como el resto de las columnas de texto. La restricción única y el CHECK que usan channel
-- se quitan y se vuelven a crear porque SQL Server no deja cambiar el tipo mientras existen.

--changeset lavarapido:notification-048-pending-delivery-nvarchar
--comment: channel y language de pending_delivery en NVARCHAR
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM sys.columns c JOIN sys.types t ON t.user_type_id = c.user_type_id WHERE c.object_id = OBJECT_ID('notification.pending_delivery') AND c.name = 'channel' AND t.name = 'varchar'
ALTER TABLE [notification].pending_delivery DROP CONSTRAINT uq_pending_delivery_channel;
ALTER TABLE [notification].pending_delivery DROP CONSTRAINT ck_pending_delivery_channel;
ALTER TABLE [notification].pending_delivery ALTER COLUMN channel NVARCHAR(10) NOT NULL;
ALTER TABLE [notification].pending_delivery ALTER COLUMN language NVARCHAR(5) NULL;
ALTER TABLE [notification].pending_delivery ADD CONSTRAINT uq_pending_delivery_channel UNIQUE (notification_id, channel);
ALTER TABLE [notification].pending_delivery ADD CONSTRAINT ck_pending_delivery_channel CHECK (channel IN (N'PUSH', N'EMAIL'));
--rollback ALTER TABLE [notification].pending_delivery DROP CONSTRAINT uq_pending_delivery_channel;
--rollback ALTER TABLE [notification].pending_delivery DROP CONSTRAINT ck_pending_delivery_channel;
--rollback ALTER TABLE [notification].pending_delivery ALTER COLUMN channel VARCHAR(10) NOT NULL;
--rollback ALTER TABLE [notification].pending_delivery ALTER COLUMN language VARCHAR(5) NULL;
--rollback ALTER TABLE [notification].pending_delivery ADD CONSTRAINT uq_pending_delivery_channel UNIQUE (notification_id, channel);
--rollback ALTER TABLE [notification].pending_delivery ADD CONSTRAINT ck_pending_delivery_channel CHECK (channel IN ('PUSH', 'EMAIL'));
