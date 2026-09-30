package com.lavarapido.notification.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentación OpenAPI. Swagger UI queda en {@code /swagger-ui.html} y solo se activa en el
 * perfil dev (application-dev.yml).
 */
@Configuration
class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    OpenAPI notificationServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("LavaRapido — notification-service")
                        .version("v1")
                        .description("""
                                Bandeja de notificaciones (leídas/no leídas, filtros, borrar), celulares \
                                para notificaciones push y mensajes del administrador. Para probar: haz \
                                login en el security-service (POST /api/v1/auth/login en el puerto 3001), \
                                copia el accessToken y pégalo en el botón Authorize."""))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
    }
}
