package com.lavarapido.notification.infrastructure.config;

import com.lavarapido.notification.application.usecase.ReminderSettings;
import com.lavarapido.notification.domain.service.EventNotificationFactory;
import com.lavarapido.notification.domain.service.StaffNotificationFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Objetos del dominio que se inyectan (el dominio no conoce Spring). @EnableAsync: el envío de
 * push corre fuera del hilo de la petición. @EnableScheduling: la tarea de recordatorios.
 */
@Configuration
@EnableAsync
@EnableScheduling
class DomainConfig {

    /** El reloj entra por el contexto para que las pruebas fijen la hora. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    EventNotificationFactory eventNotificationFactory() {
        return new EventNotificationFactory();
    }

    @Bean
    StaffNotificationFactory staffNotificationFactory() {
        return new StaffNotificationFactory();
    }

    /** Con cuánta anticipación se recuerda una reserva (app.reminders.leads, ej. "24h,1h"). */
    @Bean
    ReminderSettings reminderSettings(@Value("${app.reminders.leads:24h,1h}") List<Duration> leads,
                                      @Value("${app.reminders.batch-size:100}") int batchSize) {
        return new ReminderSettings(leads, batchSize);
    }
}
