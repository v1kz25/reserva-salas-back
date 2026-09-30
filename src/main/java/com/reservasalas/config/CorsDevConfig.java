package com.reservasalas.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Habilita CORS para el frontend en desarrollo.
 *
 * <p>Solo se activa con el perfil {@code dev}; en el resto de entornos no se permite ningún origen
 * cruzado.
 */
@Configuration
@Profile("dev")
public class CorsDevConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    /**
     * Crea la configuración con los orígenes permitidos.
     *
     * @param allowedOrigins orígenes permitidos, definidos en {@code app.cors.allowed-origins}
     */
    public CorsDevConfig(@Value("${app.cors.allowed-origins}") String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins.clone();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
    }
}
