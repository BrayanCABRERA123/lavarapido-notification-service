package com.lavarapido.notification.domain.model;

import com.lavarapido.notification.domain.exception.InvalidValueException;

/** Sistema del celular. Se guarda para saber a dónde llegan las push (iOS necesita cuenta de Apple). */
public enum DevicePlatform {
    ANDROID,
    IOS;

    public static DevicePlatform of(String value) {
        try {
            return valueOf(value == null ? "" : value.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidValueException("INVALID_PLATFORM", "Platform must be ANDROID or IOS");
        }
    }
}
