package com.lavarapido.notification.infrastructure.config;

import com.lavarapido.notification.infrastructure.adapter.out.push.PushProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * API sin estado protegida con JWT Bearer (ADR-006). CSRF apagada porque ninguna cookie lleva
 * credenciales. Todo /api/v1 exige sesión; /api/v1/admin/** además el rol ADMIN.
 * Swagger (solo perfil dev) y /actuator/health quedan abiertos.
 * /internal/** es solo para otros servicios y scripts del servidor: la llave X-Internal-Key.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class, PushProperties.class})
class SecurityConfig {

    private static final String[] PUBLIC_GET_ENDPOINTS = {
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**"
    };

    /** Rutas entre servicios (/internal/**): sin JWT, con la llave compartida INTERNAL_API_KEY. */
    @Bean
    @Order(1)
    SecurityFilterChain internalFilterChain(HttpSecurity http, ProblemDetailsSecurityHandler entryPoint,
                                            @Value("${app.internal.api-key:}") String internalApiKey) throws Exception {
        http
                .securityMatcher("/internal/**")
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new InternalApiKeyFilter(internalApiKey), AuthorizationFilter.class)
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("INTERNAL"))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(entryPoint));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain filterChain(HttpSecurity http, ProblemDetailsSecurityHandler entryPoint,
                                    JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_ENDPOINTS).permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(entryPoint))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(entryPoint));
        return http.build();
    }

    /** Convierte el claim {@code roles} ({@code ["ADMIN"]}) en authorities ({@code ROLE_ADMIN}). */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-Id"));
        configuration.setExposedHeaders(List.of("X-Correlation-Id"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
