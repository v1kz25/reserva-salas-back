package com.reservasalas.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Deja usar la consola de H2 en desarrollo: sin sesión de la aplicación, sin CSRF (la consola no
 * envía el token) y permitiendo sus marcos del mismo origen. Solo con el perfil {@code dev}.
 */
@Configuration
@Profile("dev")
public class H2ConsoleSecurityConfig {

    /**
     * Cadena de filtros exclusiva de la consola de H2, evaluada antes que la de la API.
     *
     * @param http        constructor de la cadena
     * @param consolePath ruta de la consola, definida en {@code spring.h2.console.path}
     * @return la cadena de filtros
     * @throws Exception si la configuración no es válida
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain h2ConsoleSecurityFilterChain(
            HttpSecurity http,
            @Value("${spring.h2.console.path:/h2-console}") String consolePath) throws Exception {
        http
                .securityMatcher(consolePath + "/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        return http.build();
    }
}
