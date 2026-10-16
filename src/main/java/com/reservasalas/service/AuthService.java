package com.reservasalas.service;

import com.reservasalas.exception.InvalidCredentialsException;
import com.reservasalas.exception.TooManyLoginAttemptsException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

/**
 * Comprobación de credenciales del login, con límite de intentos por email e IP del cliente.
 */
@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final LoginAttemptService loginAttemptService;

    /**
     * Crea el servicio.
     *
     * @param authenticationManager gestor de autenticación por email y contraseña
     * @param loginAttemptService   límite de intentos de inicio de sesión
     */
    public AuthService(AuthenticationManager authenticationManager, LoginAttemptService loginAttemptService) {
        this.authenticationManager = authenticationManager;
        this.loginAttemptService = loginAttemptService;
    }

    /**
     * Comprueba el email y la contraseña, salvo que se hayan agotado los intentos de ese email desde
     * esa IP. Un inicio de sesión correcto reinicia el contador.
     *
     * @param email         email introducido; no distingue mayúsculas
     * @param password      contraseña en claro
     * @param clientAddress IP del cliente
     * @return la autenticación correcta, con un
     *     {@link com.reservasalas.security.AuthenticatedUser} sin contraseña como principal
     * @throws TooManyLoginAttemptsException si se han agotado los intentos; las credenciales no se
     *     llegan a comprobar
     * @throws InvalidCredentialsException si el email no existe o la contraseña no es correcta
     */
    public Authentication authenticate(String email, String password, String clientAddress) {
        loginAttemptService.registerAttempt(email, clientAddress);
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, password));
        } catch (AuthenticationException e) {
            throw new InvalidCredentialsException();
        }
        loginAttemptService.reset(email, clientAddress);
        return authentication;
    }
}
