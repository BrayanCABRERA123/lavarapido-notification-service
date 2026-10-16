package com.lavarapido.notification.infrastructure.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Autentica las llamadas a /internal/** con la llave compartida (INTERNAL_API_KEY) que viaja en
 * X-Internal-Key, igual que security-service y booking-service. Si la llave no coincide, o no está
 * configurada, la petición no se autentica y la cadena responde 401. Comparación en tiempo constante.
 */
class InternalApiKeyFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Internal-Key";
    static final String ROLE = "ROLE_INTERNAL";

    private final byte[] expectedKey;

    InternalApiKeyFilter(String apiKey) {
        this.expectedKey = apiKey == null || apiKey.isBlank() ? null : apiKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        if (expectedKey != null && provided != null
                && MessageDigest.isEqual(expectedKey, provided.getBytes(StandardCharsets.UTF_8))) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    "internal-service", null, List.of(new SimpleGrantedAuthority(ROLE))));
        }
        chain.doFilter(request, response);
    }
}
