package com.reservasalas.service;

import com.reservasalas.exception.InvalidCredentialsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

/**
 * Comprobación de credenciales del login.
 */
@Service
public class AuthService {

    private static final Logger LOG = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;

    /**
     * Crea el servicio.
     *
     * @param authenticationManager gestor de autenticación por email y contraseña
     */
    public AuthService(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    /**
     * Comprueba el email y la contraseña.
     *
     * @param email    email introducido; no distingue mayúsculas
     * @param password contraseña en claro
     * @return la autenticación correcta, con un
     *     {@link com.reservasalas.security.AuthenticatedUser} sin contraseña como principal
     * @throws InvalidCredentialsException si el email no existe o la contraseña no es correcta
     */
    public Authentication authenticate(String email, String password) {
        try {
            return authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, password));
        } catch (AuthenticationException e) {
            LOG.info("Inicio de sesión rechazado: credenciales no válidas");
            throw new InvalidCredentialsException();
        }
    }
}
