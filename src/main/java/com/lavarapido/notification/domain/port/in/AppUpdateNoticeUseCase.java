package com.lavarapido.notification.domain.port.in;

/**
 * Se publicó un APK nuevo de la app (deploy/publish-apk.sh de lavarapido-infra): se avisa por
 * bandeja y push a quienes tienen la app instalada. Cada versión se anuncia una sola vez.
 */
public interface AppUpdateNoticeUseCase {

    /**
     * @param versionCode el versionCode del APK publicado (entero positivo)
     * @return a cuántos usuarios se les avisó (0 si esa versión ya se había anunciado)
     */
    int announce(int versionCode);
}
