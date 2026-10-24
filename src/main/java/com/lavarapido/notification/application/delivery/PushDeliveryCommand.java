package com.lavarapido.notification.application.delivery;

import com.lavarapido.notification.domain.model.DeviceToken;
import com.lavarapido.notification.domain.model.Notification;
import com.lavarapido.notification.domain.model.PendingDelivery;
import com.lavarapido.notification.domain.port.out.PushSender;

import java.util.List;
import java.util.Objects;

/** Manda la notificación por push a los celulares activos del usuario (Expo, ADR-011). */
public record PushDeliveryCommand(Notification notification, List<DeviceToken> devices, PushSender sender)
        implements DeliveryCommand {

    public PushDeliveryCommand {
        Objects.requireNonNull(notification, "notification");
        Objects.requireNonNull(sender, "sender");
        devices = List.copyOf(devices);
        if (devices.isEmpty()) {
            throw new IllegalArgumentException("A push delivery needs at least one device");
        }
    }

    @Override
    public String channel() {
        return "push";
    }

    @Override
    public void execute() {
        sender.send(notification, devices);
    }

    /** Sin los celulares: al enviarla se leen los activos de ese momento. */
    @Override
    public PendingDelivery toPending() {
        return PendingDelivery.push(notification.notificationId());
    }
}
