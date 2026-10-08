--liquibase formatted sql
-- Nuevo tipo de notificación: confirmación de canje de un cupón de fidelización (RF-026,
-- payment.promotion_redeemed via carwash.events). PROMOTION_AVAILABLE (seed 108) es otra cosa
-- ("se te asignó una promoción"); este es "ya canjeaste el cupón, aquí está la confirmación".

--changeset lavarapido:notification-025-seed-promotion-redeemed-type
--comment: Tipo PROMOTION_REDEEMED en el catálogo notification_type
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM [notification].notification_type WHERE code = N'PROMOTION_REDEEMED'
INSERT INTO [notification].notification_type (code, name, display_order)
VALUES (N'PROMOTION_REDEEMED', N'Cupón canjeado', 13);
