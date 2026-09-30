package com.lavarapido.notification.infrastructure.config;

import com.lavarapido.notification.domain.service.EventNotificationFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.time.Clock;

/**
 * Objetos del dominio que se inyectan (el dominio no conoce Spring). @EnableAsync: el envío de
 * push corre fuera del hilo de la petición.
 */
@Configuration
@EnableAsync
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
}
