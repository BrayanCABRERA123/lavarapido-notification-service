--liquibase formatted sql
-- Aviso de versión nueva de la app: al publicar un APK en el servidor (deploy/publish-apk.sh de
-- lavarapido-infra) se avisa por bandeja y push a quienes tienen la app instalada.

--changeset lavarapido:notification-038-seed-app-update-type
--comment: Tipo APP_UPDATE_AVAILABLE en el catálogo notification_type
MERGE [notification].notification_type AS target
USING (VALUES
    (N'APP_UPDATE_AVAILABLE', N'Versión nueva de la app', 24)
) AS source (code, name, display_order)
ON target.code = source.code
WHEN NOT MATCHED THEN
    INSERT (code, name, display_order) VALUES (source.code, source.name, source.display_order);
--rollback DELETE FROM [notification].notification_type WHERE code = N'APP_UPDATE_AVAILABLE';
