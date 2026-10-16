package com.reservasalas.controller;

import com.reservasalas.dto.LoginRequest;
import com.reservasalas.dto.UserDto;
import com.reservasalas.security.AuthenticatedUser;
import com.reservasalas.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de inicio y cierre de sesión, token CSRF y usuario actual.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final SecurityContextRepository securityContextRepository;
    private final CsrfTokenRepository csrfTokenRepository;
    private final SessionAuthenticationStrategy sessionFixationProtection =
            new ChangeSessionIdAuthenticationStrategy();
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();
    private final SecurityContextHolderStrategy contextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    /**
     * Crea el controlador.
     *
     * @param authService               servicio de autenticación
     * @param securityContextRepository repositorio donde se guarda la sesión autenticada
     * @param csrfTokenRepository       repositorio del token CSRF en cookie
     */
    public AuthController(AuthService authService,
                          SecurityContextRepository securityContextRepository,
                          CsrfTokenRepository csrfTokenRepository) {
        this.authService = authService;
        this.securityContextRepository = securityContextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
    }

    /**
     * Emite la cookie {@code XSRF-TOKEN} sin crear sesión.
     *
     * @param csrfToken token CSRF de la petición; leerlo hace que se escriba la cookie si no existía
     * @return respuesta vacía {@code 204}
     */
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf(CsrfToken csrfToken) {
        csrfToken.getToken();
        return ResponseEntity.noContent().build();
    }

    /**
     * Inicia sesión. Si ya había sesión, se cambia su identificador (protección frente a fijación de
     * sesión) y se emite un token CSRF nuevo.
     *
     * @param login    credenciales
     * @param request  petición en curso
     * @param response respuesta en curso, donde se escriben las cookies
     * @return el usuario autenticado
     */
    @PostMapping("/login")
    public ResponseEntity<UserDto> login(@Valid @RequestBody LoginRequest login,
                                         HttpServletRequest request,
                                         HttpServletResponse response) {
        Authentication authentication = authService.authenticate(login.email(), login.password());

        sessionFixationProtection.onAuthentication(authentication, request, response);
        SecurityContext context = contextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        renewCsrfToken(request, response);

        return ResponseEntity.ok(UserDto.from((AuthenticatedUser) authentication.getPrincipal()));
    }

    /**
     * Cierra la sesión: la invalida y emite un token CSRF nuevo.
     *
     * @param authentication autenticación de la sesión
     * @param request        petición en curso
     * @param response       respuesta en curso
     * @return respuesta vacía {@code 204}
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication,
                                       HttpServletRequest request,
                                       HttpServletResponse response) {
        logoutHandler.logout(request, response, authentication);
        renewCsrfToken(request, response);
        return ResponseEntity.noContent().build();
    }

    /**
     * Obtiene el usuario de la sesión, con los datos guardados en ella al iniciar sesión. No hace
     * falta leerlos de nuevo de la base de datos: si cambian el rol o el estado del usuario, su
     * sesión se cierra en la siguiente petición (ver
     * {@link com.reservasalas.security.SessionValidationFilter}), y si un administrador cambia su
     * propio nombre se actualiza su sesión. El cambio de nombre de otro usuario se ve en su
     * siguiente inicio de sesión.
     *
     * @param user usuario autenticado
     * @return el usuario
     */
    @GetMapping("/me")
    public ResponseEntity<UserDto> me(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(UserDto.from(user));
    }

    /**
     * Sustituye la cookie {@code XSRF-TOKEN} por un token nuevo, para que el token cambie al cambiar
     * el estado de autenticación.
     */
    private void renewCsrfToken(HttpServletRequest request, HttpServletResponse response) {
        csrfTokenRepository.saveToken(csrfTokenRepository.generateToken(request), request, response);
    }
}
