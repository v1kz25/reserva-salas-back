package com.reservasalas.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Habilita CORS para el frontend en desarrollo, con credenciales (cookies de sesión y CSRF).
 *
 * <p>Solo se activa con el perfil {@code dev}; en el resto de entornos no se permite ningún origen
 * cruzado. La aplica el filtro CORS de Spring Security, antes de exigir sesión, para que las
 * peticiones de comprobación previa ({@code OPTIONS}) no respondan {@code 401}.
 */
@Configuration
@Profile("dev")
public class CorsDevConfig {

    private final List<String> allowedOrigins;

    /**
     * Crea la configuración con los orígenes permitidos.
     *
     * @param allowedOrigins orígenes permitidos, definidos en {@code app.cors.allowed-origins}
     */
    public CorsDevConfig(@Value("${app.cors.allowed-origins}") String[] allowedOrigins) {
        this.allowedOrigins = List.of(allowedOrigins);
    }

    /**
     * Configuración CORS de la API.
     *
     * @return la configuración, aplicada a {@code /api/**}
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
