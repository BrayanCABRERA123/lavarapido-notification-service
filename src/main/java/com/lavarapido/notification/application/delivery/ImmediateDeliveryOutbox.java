package com.lavarapido.notification.application.delivery;

import com.lavarapido.notification.application.delivery.DeliveryInvoker.DeliveryReport;
import com.lavarapido.notification.domain.model.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/** Ejecuta las entregas en el momento, sin guardarlas ni reintentarlas (pruebas y uso sin base). */
public final class ImmediateDeliveryOutbox implements DeliveryOutbox {

    private static final Logger log = LoggerFactory.getLogger(ImmediateDeliveryOutbox.class);

    private final DeliveryInvoker invoker = new DeliveryInvoker();

    @Override
    public void submit(Notification saved, List<DeliveryCommand> deliveries) {
        DeliveryReport report = invoker.run(saved.notificationId(), deliveries);
        log.info("Notification {} delivered by {}{}", saved.notificationId(),
                report.delivered().isEmpty() ? "inbox only" : report.delivered(),
                report.failed().isEmpty() ? "" : ", failed by " + report.failed());
    }
}
