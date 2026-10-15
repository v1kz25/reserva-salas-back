package com.reservasalas.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Autenticación por email y contraseña contra los usuarios de la base de datos, con contraseñas
 * guardadas en BCrypt.
 */
@Configuration
public class AuthenticationConfig {

    /**
     * Codificador de contraseñas.
     *
     * @return codificador BCrypt
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Gestor de autenticación que usa el login. Si el email no existe, igualmente compara la
     * contraseña con un hash ficticio para no revelar por el tiempo de respuesta qué emails existen.
     *
     * @param userDetailsService servicio que carga los usuarios por email
     * @param passwordEncoder    codificador de contraseñas
     * @return el gestor de autenticación
     */
    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}
